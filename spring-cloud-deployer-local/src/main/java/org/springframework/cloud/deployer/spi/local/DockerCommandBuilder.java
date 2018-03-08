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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 */
public class DockerCommandBuilder implements CommandBuilder {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/**
	 * Name of the deployment property used to specify the container name pattern to use.
	 */
	public static final String DOCKER_CONTAINER_NAME_KEY = AppDeployer.PREFIX + "docker.container.name";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public String[] buildExecutionCommand(AppDeploymentRequest request, Map<String, String> appInstanceEnv,
										  Optional<Integer> appInstanceNumber) {
		List<String> commands = addDockerOptions(request, appInstanceEnv, appInstanceNumber);
		commands.addAll(request.getCommandlineArguments());
		logger.debug("Docker Command = " + commands);
		return commands.toArray(new String[0]);
	}

	private List<String> addDockerOptions(AppDeploymentRequest request, Map<String, String> appInstanceEnv,
										  Optional<Integer> appInstanceNumber) {
		List<String> commands = new ArrayList<>();
		commands.add("docker");
		commands.add("run");

		// Add env vars
		for (String env : appInstanceEnv.keySet()) {
			commands.add("-e");
			commands.add(String.format("%s=%s", env, appInstanceEnv.get(env)));
		}

		setPort(commands, appInstanceEnv);

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

	private void setPort(List<String> commands, Map<String, String> appInstanceEnv) {

		String port;

		if(appInstanceEnv.containsKey(AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON)) {
			Map<String, String> properties = new HashMap<>();

			try {
				properties.putAll(OBJECT_MAPPER.readValue(appInstanceEnv.get(AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON), new TypeReference<HashMap<String,Object>>() {}));
			}
			catch (IOException e) {
				throw new IllegalArgumentException("Unable to determine server port from SPRING_APPLICATION_JSON");
			}

			port = properties.get(LocalAppDeployer.SERVER_PORT_KEY);
		}
		else {
			port = appInstanceEnv.get(LocalAppDeployer.SERVER_PORT_KEY);
		}

		if(StringUtils.hasText(port)) {
			commands.add("-p");
			commands.add(String.format("%s:%s", port, port));
		}
	}
}
