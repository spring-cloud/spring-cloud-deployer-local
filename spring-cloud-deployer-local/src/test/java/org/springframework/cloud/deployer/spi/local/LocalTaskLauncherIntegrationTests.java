/*
 * Copyright 2016-2017 the original author or authors.
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.local.LocalTaskLauncherIntegrationTests.Config;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.test.AbstractIntegrationTests;
import org.springframework.cloud.deployer.spi.test.AbstractTaskLauncherIntegrationTests;
import org.springframework.cloud.deployer.spi.test.EventuallyMatcher;
import org.springframework.cloud.deployer.spi.test.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.SocketUtils;

/**
 * Integration tests for {@link LocalTaskLauncher}.
 *
 * Now supports running with Docker images for tests, just set this env var:
 *
 *   SPRING_CLOUD_DEPLOYER_SPI_TEST_USE_DOCKER=true
 *
 * @author Eric Bottard
 * @author Janne Valkealahti
 *
 */
@SpringBootTest(classes = {Config.class, AbstractIntegrationTests.Config.class}, value = {
		"maven.remoteRepositories.springRepo.url=https://repo.spring.io/libs-snapshot" })
public class LocalTaskLauncherIntegrationTests extends AbstractTaskLauncherIntegrationTests {

	@Autowired
	private TaskLauncher taskLauncher;

	@Value("${spring-cloud-deployer-spi-test-use-docker:false}")
	private boolean useDocker;

	@Override
	protected TaskLauncher provideTaskLauncher() {
		return taskLauncher;
	}

	@Override
	protected Resource testApplication() {
		if (useDocker) {
			log.info("Using Docker image for tests");
			return new DockerResource("springcloud/spring-cloud-deployer-spi-test-app:latest");
		}
		return super.testApplication();
	}

	@Override
	protected String randomName() {
		if (LocalDeployerUtils.isWindows()) {
			// tweak random dir name on win to be shorter
			String uuid = UUID.randomUUID().toString();
			long l = ByteBuffer.wrap(uuid.toString().getBytes()).getLong();
			return name.getMethodName() + Long.toString(l, Character.MAX_RADIX);
		}
		else {
			return super.randomName();
		}
	}

	@Test
	public void testPassingServerPortViaCommandLineArgs() throws InterruptedException {
		Map<String, String> appProperties = new HashMap();
		appProperties.put("killDelay", "0");
		appProperties.put("exitCode", "0");

		AppDefinition definition = new AppDefinition(this.randomName(), appProperties);

		Resource resource = this.testApplication();

		List<String> commandLineArgs = new ArrayList<>(1);
		commandLineArgs.add(LocalTaskLauncher.SERVER_PORT_KEY_PREFIX + SocketUtils.findAvailableTcpPort(LocalTaskLauncher.DEFAULT_SERVER_PORT));

		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, null, commandLineArgs);

		this.log.info("Launching {}...", request.getDefinition().getName());

		String launchId = this.taskLauncher().launch(request);
		Timeout timeout = this.deploymentTimeout();

		Assert.assertThat(launchId, EventuallyMatcher.eventually(this.hasStatusThat(Matchers.hasProperty("state", Matchers.is(LaunchState.complete))), timeout.maxAttempts, timeout.pause));

		this.taskLauncher().destroy(definition.getName());
	}

	@Configuration
	@EnableConfigurationProperties(LocalDeployerProperties.class)
	public static class Config {

		@Bean
		public TaskLauncher taskLauncher(LocalDeployerProperties properties) {
			return new LocalTaskLauncher(properties);
		}
	}

}
