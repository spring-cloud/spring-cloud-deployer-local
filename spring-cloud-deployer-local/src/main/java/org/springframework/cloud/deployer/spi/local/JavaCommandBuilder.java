/*
 * Copyright 2016-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.deployer.spi.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.util.ByteSizeUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.deployer.spi.local.LocalDeployerProperties.PREFIX;

/**
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @author Thomas Risberg
 * @author Michael Minella
 */
public class JavaCommandBuilder implements CommandBuilder {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final String MAIN = "main";

	private static final String CLASSPATH = "classpath";

	private LocalDeployerProperties properties;

	public JavaCommandBuilder(LocalDeployerProperties properties) {
		this.properties = properties;
	}

	@Override
	public String[] buildExecutionCommand(AppDeploymentRequest request, Map<String, String> appInstanceEnv,
			Optional<Integer> appInstanceNumber) {
		ArrayList<String> commands = new ArrayList<>();
		Map<String, String> deploymentProperties = request.getDeploymentProperties();
		commands.add(properties.getJavaCmd());
		// Add Java System Properties (ie -Dmy.prop=val) before main class or -jar
		addJavaOptions(commands, deploymentProperties, properties);
		addJavaExecutionOptions(commands, request);
		commands.addAll(request.getCommandlineArguments());
		logger.debug("Java Command = " + commands);
		return commands.toArray(new String[0]);
	}

	protected void addJavaOptions(List<String> commands, Map<String, String> deploymentProperties,
			LocalDeployerProperties localDeployerProperties) {
		String memory = null;
		if (deploymentProperties.containsKey(AppDeployer.MEMORY_PROPERTY_KEY)) {
			memory = "-Xmx" +
					ByteSizeUtils.parseToMebibytes(deploymentProperties.get(AppDeployer.MEMORY_PROPERTY_KEY)) + "m";
		}

		String javaOptsString = getValue(deploymentProperties, "javaOpts");
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
		Map<String, String> deploymentProperties = request.getDeploymentProperties();
		if (containsKey(deploymentProperties, MAIN) || containsKey(deploymentProperties, CLASSPATH)) {
			Assert.isTrue(containsKey(deploymentProperties, MAIN)
							&& containsKey(deploymentProperties, CLASSPATH),
					PREFIX + "." + MAIN + " and " + PREFIX + "." + CLASSPATH +
							" deployment properties are both required if either is provided.");
			commands.add("-cp");
			commands.add(getValue(deploymentProperties, CLASSPATH));
			commands.add(getValue(deploymentProperties, MAIN));
		}
		else {
			commands.add("-jar");
			Resource resource = request.getResource();
			try {
				commands.add(resource.getFile().getAbsolutePath());
			}
			catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	private boolean containsKey(Map<String, String> deploymentProperties, String propertyName) {
		return deploymentProperties.containsKey(PREFIX + "." + propertyName);
	}

	private String getValue(Map<String, String> deploymentProperties, String propertyName) {
		return deploymentProperties.get(PREFIX + "." + propertyName);
	}

}
