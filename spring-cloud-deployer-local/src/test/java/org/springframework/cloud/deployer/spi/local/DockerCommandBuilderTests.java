/*
 * Copyright 2017-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DockerCommandBuilder}.
 *
 * @author Eric Bottard
 * @author Christian Tzolov
 */
public class DockerCommandBuilderTests {

	@Test
	public void testContainerName() {
		AppDefinition appDefinition = new AppDefinition("foo", null);
		Resource resource = new DockerResource("foo/bar");
		Map<String, String> deploymentProperties = Collections.singletonMap(DockerCommandBuilder.DOCKER_CONTAINER_NAME_KEY, "gogo");
		AppDeploymentRequest request = new AppDeploymentRequest(appDefinition, resource, deploymentProperties);

		ProcessBuilder builder = new DockerCommandBuilder(null, true)
				.buildExecutionCommand(request, new HashMap<>(), "deployerId", Optional.of(1),
						new LocalDeployerProperties(), Optional.empty());
		assertThat(builder.command()).containsAnyElementsOf(Arrays.asList("docker", "run", "--rm", "--name=gogo-1",
				"foo/bar", "deployerId=deployerId"));
	}

	@Test
	public void testContainerNameWithDockerNetwork() {
		AppDefinition appDefinition = new AppDefinition("foo", null);
		Resource resource = new DockerResource("foo/bar");
		Map<String, String> deploymentProperties = Collections.singletonMap(DockerCommandBuilder.DOCKER_CONTAINER_NAME_KEY, "gogo");
		AppDeploymentRequest request = new AppDeploymentRequest(appDefinition, resource, deploymentProperties);
		ProcessBuilder builder = new DockerCommandBuilder("scdf_default", true)
				.buildExecutionCommand(request, new HashMap<>(), "deployerId", Optional.of(1),
						new LocalDeployerProperties(), Optional.empty());
		assertThat(builder.command()).containsAnyElementsOf(Arrays.asList("docker", "run", "--network", "scdf_default",
				"--rm", "--name=gogo-1", "foo/bar"));
	}

	@Test
	public void testContainerNameWithDockerNetworkAndKeepContainers() {
		AppDefinition appDefinition = new AppDefinition("foo", null);
		Resource resource = new DockerResource("foo/bar");
		Map<String, String> deploymentProperties = Collections.singletonMap(DockerCommandBuilder.DOCKER_CONTAINER_NAME_KEY, "gogo");
		AppDeploymentRequest request = new AppDeploymentRequest(appDefinition, resource, deploymentProperties);
		ProcessBuilder builder = new DockerCommandBuilder("scdf_default", false)
				.buildExecutionCommand(request, new HashMap<>(), "deployerId", Optional.of(1),
						new LocalDeployerProperties(), Optional.empty());
		assertThat(builder.command()).containsAnyElementsOf(Arrays.asList("docker", "run", "--network", "scdf_default",
				"--name=gogo-1", "foo/bar"));
		assertThat(builder.command()).doesNotContain("--rm");
	}

	@Test
	public void testUseLocalDeployerPropertiesToKeepStoppedContainer() {
		AppDefinition appDefinition = new AppDefinition("foo", null);
		Resource resource = new DockerResource("foo/bar");
		Map<String, String> deploymentProperties = Collections.singletonMap(DockerCommandBuilder.DOCKER_CONTAINER_NAME_KEY, "gogo");
		AppDeploymentRequest request = new AppDeploymentRequest(appDefinition, resource, deploymentProperties);

		LocalDeployerProperties localDeployerProperties = new LocalDeployerProperties();
		localDeployerProperties.getDocker().setDeleteContainerOnExit(false);

		ProcessBuilder builder = new DockerCommandBuilder("scdf_default", true)
				.buildExecutionCommand(request, new HashMap<>(), "deployerId", Optional.of(1),
						localDeployerProperties, Optional.empty());
		assertThat(builder.command()).containsAnyElementsOf(Arrays.asList("docker", "run", "--network", "scdf_default",
				"--name=gogo-1", "foo/bar"));
		assertThat(builder.command()).doesNotContain("--rm");
	}

	@Test
	public void testSpringApplicationJSON() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		LocalAppDeployer deployer = new LocalAppDeployer(properties);
		AppDefinition definition = new AppDefinition("foo", Collections.singletonMap("foo", "bar"));
		Resource resource = new DockerResource("foo/bar");
		Map<String, String> deploymentProperties = new HashMap<>();
		deploymentProperties.put(LocalDeployerProperties.DEBUG_ADDRESS, "*:9999");
		deploymentProperties.put(LocalDeployerProperties.DEBUG_SUSPEND, "y");
		deploymentProperties.put(LocalDeployerProperties.INHERIT_LOGGING, "true");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, deploymentProperties);
		ProcessBuilder builder = deployer.buildProcessBuilder(request, request.getDefinition().getProperties(), Optional.of(1), "foo");

		String SAJ = LocalDeployerUtils.isWindows() ? "SPRING_APPLICATION_JSON={\\\"foo\\\":\\\"bar\\\"}" : "SPRING_APPLICATION_JSON={\"foo\":\"bar\"}";
		assertThat(builder.command()).contains("-e", SAJ);
	}

}
