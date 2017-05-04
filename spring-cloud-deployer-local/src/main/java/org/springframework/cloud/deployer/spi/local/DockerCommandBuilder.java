/*
 * Copyright 2016 the original author or authors.
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;

/**
 * @author Ilayaperumal Gopinathan
 */
public class DockerCommandBuilder implements CommandBuilder {

	private static final String DOCKER_CONTAINER_NAME_KEY = "docker.container.name";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public String[] buildExecutionCommand(AppDeploymentRequest request, Map<String, String> args, Optional<Integer> appInstanceNumber) {
		List<String> commands = addDockerOptions(request, args, appInstanceNumber);
		logger.debug("Docker Command = " + commands);
		return commands.toArray(new String[0]);
	}

	private List<String> addDockerOptions(AppDeploymentRequest request, Map<String, String> args, Optional<Integer> appInstanceNumber) {
		List<String> commands = new ArrayList<>();
		commands.add("docker");
		commands.add("run");
		DockerResource dockerResource = (DockerResource) request.getResource();
		for (Map.Entry<String, String> entry : args.entrySet()) {
			if (entry.getKey().equals(LocalAppDeployer.SERVER_PORT_KEY)) {
				commands.add("-p");
				commands.add(String.format("%s:8080", args.get(entry.getKey())));
			} else if (entry.getKey().equals(DOCKER_CONTAINER_NAME_KEY)) {
				if(appInstanceNumber.isPresent()) {
					commands.add(String.format("--name=%s-%d", entry.getValue(), appInstanceNumber.get()));
				} else {
					commands.add(String.format("--name=%s", args.get(entry.getValue())));
				}
			}
			else {
				commands.add("-e");
				commands.add(String.format("%s=%s", entry.getKey(), entry.getValue()));
			}
		}
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
