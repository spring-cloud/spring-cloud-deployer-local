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

import static org.hamcrest.collection.IsArrayContainingInOrder.arrayContaining;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.hamcrest.collection.IsArrayContainingInOrder;
import org.junit.Test;

import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.Resource;

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

}
