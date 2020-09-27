/*
 * Copyright 2016-2020 the original author or authors.
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
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.util.StringUtils;

/**
 * Command builder used to craft the command used when running apps inside docker containers.
 *
 * @author Ilayaperumal Gopinathan
 * @author Eric Bottard
 * @author Henryk Konsek
 * @author Thomas Risberg
 * @author Michael Minella
 * @author Christian Tzolov
 */
public class DockerCommandBuilder implements CommandBuilder {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/**
	 * Name of the deployment property used to specify the container name pattern to use.
	 */
	public static final String DOCKER_CONTAINER_NAME_KEY = AppDeployer.PREFIX + "docker.container.name";

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final String dockerNetwork;
	private final boolean deleteContainerOnExit;

	public DockerCommandBuilder(String dockerNetwork, boolean deleteContainerOnExit) {
		this.dockerNetwork = dockerNetwork;
		this.deleteContainerOnExit = deleteContainerOnExit;
	}

	@Override
	public int getPortSuggestion(LocalDeployerProperties localDeployerProperties) {
		return ThreadLocalRandom.current().nextInt(localDeployerProperties.getDocker().getPortRange().getLow(),
				localDeployerProperties.getDocker().getPortRange().getHigh());
	}

	@Override
	public URL getBaseUrl(String deploymentId, int index, int port) {
		try {
			return new URL("http", String.format("%s-%d", deploymentId, index), port, "");
		}
		catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public ProcessBuilder buildExecutionCommand(AppDeploymentRequest request, Map<String, String> appInstanceEnv, String deployerId,
			Optional<Integer> appInstanceNumber, LocalDeployerProperties localDeployerProperties,
			Optional<DebugAddress> debugAddressOption) {

		appInstanceEnv.put("deployerId", deployerId);
		List<String> commands = addDockerOptions(request, appInstanceEnv, appInstanceNumber, localDeployerProperties, debugAddressOption);
		commands.addAll(request.getCommandlineArguments());
		logger.debug("Docker Command = " + commands);
		return new ProcessBuilder(Arrays.asList(AbstractLocalDeployerSupport.windowsSupport(commands.toArray(new String[0]))));
	}

	private List<String> addDockerOptions(AppDeploymentRequest request, Map<String, String> appInstanceEnv,
			Optional<Integer> appInstanceNumber, LocalDeployerProperties localDeployerProperties,
			Optional<DebugAddress> debugAddressOption) {

		List<String> commands = new ArrayList<>();
		commands.add("docker");
		commands.add("run");

		if (StringUtils.hasText(this.dockerNetwork)) {
			commands.add("--network");
			commands.add(this.dockerNetwork);
		}

		if (this.deleteContainerOnExit && localDeployerProperties.getDocker().isDeleteContainerOnExit()) {
			commands.add("--rm");
		}

		// Add env vars
		for (String env : appInstanceEnv.keySet()) {
			commands.add("-e");
			commands.add(String.format("%s=%s", env, appInstanceEnv.get(env)));
		}

		debugAddressOption.ifPresent(debugAddress -> {
			String debugCommand = debugAddress.getDebugCommand();
			logger.debug("Deploying app with Debug Command = [{}]", debugCommand);

			commands.add("-e");
			commands.add("JAVA_TOOL_OPTIONS=" + debugCommand);
			commands.add("-p");
			commands.add(String.format("%s:%s", debugAddress.getPort(), debugAddress.getPort()));
		});

		String port = getPort(appInstanceEnv);

		if (StringUtils.hasText(port)) {
			commands.add("-p");
			commands.add(String.format("%s:%s", port, port));
		}


		if (request.getDeploymentProperties().containsKey(DOCKER_CONTAINER_NAME_KEY)) {
			if (appInstanceNumber.isPresent()) {
				commands.add(String.format("--name=%s-%d", request.getDeploymentProperties().get(DOCKER_CONTAINER_NAME_KEY), appInstanceNumber.get()));
			}
			else {
				commands.add(String.format("--name=%s", request.getDeploymentProperties().get(DOCKER_CONTAINER_NAME_KEY)));
			}
		}
		else {
			String group = request.getDeploymentProperties().get(AppDeployer.GROUP_PROPERTY_KEY);
			if (StringUtils.hasText(group)) {
				String deploymentId = String.format("%s.%s", group, request.getDefinition().getName());
				int index = appInstanceNumber.orElse(0);
				commands.add(String.format("--name=%s-%d", deploymentId, index));
			}
		}

		DockerResource dockerResource = (DockerResource) request.getResource();

		try {
			String dockerImageURI = dockerResource.getURI().toString();
			commands.add(dockerImageURI.substring("docker:".length()));
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}

		return commands;
	}

	private String getPort(Map<String, String> appInstanceEnv) {
		if (appInstanceEnv.containsKey(AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON)) {
			try {
				new HashMap<>((OBJECT_MAPPER.readValue(
						appInstanceEnv.get(AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON),
						new TypeReference<HashMap<String, String>>() {}))
				).get(LocalAppDeployer.SERVER_PORT_KEY);
			}
			catch (IOException e) {
				throw new IllegalArgumentException("Unable to determine server port from SPRING_APPLICATION_JSON");
			}
		}
		return appInstanceEnv.get(LocalAppDeployer.SERVER_PORT_KEY);
	}
}
