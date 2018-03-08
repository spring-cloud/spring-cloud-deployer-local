/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.local;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.Resource;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.collection.IsArrayContainingInOrder.arrayContaining;
import static org.junit.Assert.assertThat;

/**
 * Unit tests for {@link DockerCommandBuilder}.
 *
 * @author Eric Bottard
 */
public class DockerCommandBuilderTests {

	private DockerCommandBuilder commandBuilder = new DockerCommandBuilder();

	@Test
	public void testContainerName() {
		AppDefinition appDefinition = new AppDefinition("foo", null);
		Resource resource = new DockerResource("foo/bar");
		Map<String, String> deploymentProperties = Collections.singletonMap(DockerCommandBuilder.DOCKER_CONTAINER_NAME_KEY, "gogo");
		AppDeploymentRequest request = new AppDeploymentRequest(appDefinition, resource, deploymentProperties);
		String[] command = commandBuilder.buildExecutionCommand(request, Collections.emptyMap(), Optional.of(1));

		assertThat(command, arrayContaining("docker", "run", "--name=gogo-1", "foo/bar"));
	}

	@Test
	public void testSpringApplicationJSON() throws Exception {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		LocalAppDeployer deployer = new LocalAppDeployer(properties);
		AppDefinition definition = new AppDefinition("foo", Collections.singletonMap("foo","bar"));
		Resource resource = new DockerResource("foo/bar");
		Map<String, String> deploymentProperties = new HashMap<>();
		deploymentProperties.put(LocalDeployerProperties.DEBUG_PORT, "9999");
		deploymentProperties.put(LocalDeployerProperties.DEBUG_SUSPEND, "y");
		deploymentProperties.put(LocalDeployerProperties.INHERIT_LOGGING, "true");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, deploymentProperties);
		ProcessBuilder builder = deployer.buildProcessBuilder(request, request.getDefinition().getProperties(), Optional.of(1), "foo" );

		String SAJ = LocalDeployerUtils.isWindows() ? "SPRING_APPLICATION_JSON={\\\"foo\\\":\\\"bar\\\"}" : "SPRING_APPLICATION_JSON={\"foo\":\"bar\"}";
		assertThat(builder.command(), hasItems("-e", SAJ));

	}

}
