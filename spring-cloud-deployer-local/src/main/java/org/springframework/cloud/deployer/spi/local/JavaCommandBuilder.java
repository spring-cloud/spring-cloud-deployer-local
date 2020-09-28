/*
 * Copyright 2016-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.deployer.spi.local;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.util.ByteSizeUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

/**
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @author Thomas Risberg
 * @author Michael Minella
 */
public class JavaCommandBuilder implements CommandBuilder {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final LocalDeployerProperties properties;

	public JavaCommandBuilder(LocalDeployerProperties properties) {
		this.properties = properties;
	}

	@Override
	public int getPortSuggestion(LocalDeployerProperties localDeployerProperties) {
		return ThreadLocalRandom.current().nextInt(localDeployerProperties.getPortRange().getLow(),
				localDeployerProperties.getPortRange().getHigh());
	}

	@Override
	public URL getBaseUrl(String deploymentId, int index, int port) {
		try {
			return new URL("http", Inet4Address.getLocalHost().getHostAddress(), port, "");
		}
		catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public ProcessBuilder buildExecutionCommand(AppDeploymentRequest request, Map<String, String> appInstanceEnv,
			String deployerId, Optional<Integer> appInstanceNumber, LocalDeployerProperties localDeployerProperties,
			Optional<DebugAddress> debugAddressOption) {
		ArrayList<String> commands = new ArrayList<>();
		Map<String, String> deploymentProperties = request.getDeploymentProperties();
		commands.add(bindDeploymentProperties(deploymentProperties).getJavaCmd());

		debugAddressOption.ifPresent(debugAddress -> {
			commands.add(debugAddress.getDebugCommand());
		});

		// Add Java System Properties (ie -Dmy.prop=val) before main class or -jar
		addJavaOptions(commands, deploymentProperties, properties);
		addJavaExecutionOptions(commands, request);
		commands.addAll(request.getCommandlineArguments());
		logger.debug("Java Command = " + commands);

		ProcessBuilder builder = new ProcessBuilder(AbstractLocalDeployerSupport.windowsSupport(commands.toArray(new String[0])));

		builder.environment().putAll(appInstanceEnv);

		return builder;
	}

	protected void addJavaOptions(List<String> commands, Map<String, String> deploymentProperties,
			LocalDeployerProperties localDeployerProperties) {
		String memory = null;
		if (deploymentProperties.containsKey(AppDeployer.MEMORY_PROPERTY_KEY)) {
			memory = "-Xmx" +
					ByteSizeUtils.parseToMebibytes(deploymentProperties.get(AppDeployer.MEMORY_PROPERTY_KEY)) + "m";
		}

		String javaOptsString = bindDeploymentProperties(deploymentProperties).getJavaOpts();
		if (javaOptsString == null && memory != null) {
			commands.add(memory);
		}

		if (javaOptsString != null) {
			String[] javaOpts = StringUtils.tokenizeToStringArray(javaOptsString, " ");
			boolean noJavaMemoryOption = Stream.of(javaOpts).noneMatch(s -> s.startsWith("-Xmx"));
			if (noJavaMemoryOption && memory != null) {
				commands.add(memory);
			}
			commands.addAll(Arrays.asList(javaOpts));
		}
		else {
			if (localDeployerProperties.getJavaOpts() != null) {
				String[] javaOpts = StringUtils.tokenizeToStringArray(localDeployerProperties.getJavaOpts(), " ");
				commands.addAll(Arrays.asList(javaOpts));
			}
		}
	}

	protected void addJavaExecutionOptions(List<String> commands, AppDeploymentRequest request) {
		commands.add("-jar");
		Resource resource = request.getResource();
		try {
			commands.add(resource.getFile().getAbsolutePath());
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * This will merge the deployment properties that were passed in at runtime with the deployment properties
	 * of the Deployer instance.
	 * @param runtimeDeploymentProperties deployment properties passed in at runtime
	 * @return merged deployer properties
	 */
	protected LocalDeployerProperties bindDeploymentProperties(Map<String, String> runtimeDeploymentProperties) {
		LocalDeployerProperties copyOfDefaultProperties = new LocalDeployerProperties(this.properties);
		return new Binder(new MapConfigurationPropertySource(runtimeDeploymentProperties))
				.bind(LocalDeployerProperties.PREFIX, Bindable.ofInstance(copyOfDefaultProperties))
				.orElse(copyOfDefaultProperties);
	}

}
