/*
 * Copyright 2016-2017 the original author or authors.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;

/**
 * Command builder used to craft the command used when running apps inside docker containers.
 *
 * @author Ilayaperumal Gopinathan
 * @author Eric Bottard
 * @author Henryk Konsek
 * @author Thomas Risberg
 */
public class DockerCommandBuilder implements CommandBuilder {

	/**
	 * Name of the deployment property used to specify the container name pattern to use.
	 */
	public static final String DOCKER_CONTAINER_NAME_KEY = AppDeployer.PREFIX + "docker.container.name";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public String[] buildExecutionCommand(AppDeploymentRequest request, Map<String, String> appInstanceEnv,
										  Map<String, String> appProperties, Optional<Integer> appInstanceNumber) {
		List<String> commands = addDockerOptions(request, appInstanceEnv, appProperties, appInstanceNumber);
		// Add appProperties
		for (String prop : appProperties.keySet()) {
			commands.add(String.format("--%s=%s", prop, appProperties.get(prop)));
		}
		commands.addAll(request.getCommandlineArguments());
		logger.debug("Docker Command = " + commands);
		return commands.toArray(new String[0]);
	}

	private List<String> addDockerOptions(AppDeploymentRequest request, Map<String, String> appInstanceEnv,
										  Map<String, String> appProperties, Optional<Integer> appInstanceNumber) {
		List<String> commands = new ArrayList<>();
		commands.add("docker");
		commands.add("run");
		// Add env vars
		for (String env : appInstanceEnv.keySet()) {
			commands.add("-e");
			commands.add(String.format("%s=%s", env, appInstanceEnv.get(env)));
		}
		if (appProperties.containsKey(LocalAppDeployer.SERVER_PORT_KEY)) {
			String port = appProperties.get(LocalAppDeployer.SERVER_PORT_KEY);
			commands.add("-p");
			commands.add(String.format("%s:%s", port, port));
		}
		if(request.getDeploymentProperties().containsKey(DOCKER_CONTAINER_NAME_KEY)) {
			if(appInstanceNumber.isPresent()) {
				commands.add(String.format("--name=%s-%d", request.getDeploymentProperties().get(DOCKER_CONTAINER_NAME_KEY), appInstanceNumber.get()));
			} else {
				commands.add(String.format("--name=%s", request.getDeploymentProperties().get(DOCKER_CONTAINER_NAME_KEY)));
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
}
