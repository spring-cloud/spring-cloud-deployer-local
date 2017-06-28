/*
 * Copyright 2016 the original author or authors.
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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.deployed;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.unknown;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.local.LocalAppDeployerIntegrationTests.Config;
import org.springframework.cloud.deployer.spi.test.AbstractAppDeployerIntegrationTests;
import org.springframework.cloud.deployer.spi.test.AbstractIntegrationTests;
import org.springframework.cloud.deployer.spi.test.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Integration tests for {@link LocalAppDeployer}.
 *
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
@SpringBootTest(classes = {Config.class, AbstractIntegrationTests.Config.class}, value = {
		"maven.remoteRepositories.springRepo.url=https://repo.spring.io/libs-snapshot" })
public class LocalAppDeployerIntegrationTests extends AbstractAppDeployerIntegrationTests {

	@Autowired
	private AppDeployer appDeployer;

	@Override
	protected AppDeployer provideAppDeployer() {
		return appDeployer;
	}

	@Test
	public void testFailureToCallShutdownOnUndeploy() {
		Map<String, String> properties = new HashMap<>();
		// disable shutdown endpoint
		properties.put("endpoints.shutdown.enabled", "false");
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying {}...", request.getDefinition().getName());

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
	}

	@Test
	// was triggered by GH-50 and subsequently GH-55
	public void testNoStdoutStderrOnInheritLoggingAndNoNPEOnGetAttributes() {
		Map<String, String> properties = new HashMap<>();
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, Collections.singletonMap(LocalDeployerProperties.INHERIT_LOGGING, "true"));

		AppDeployer deployer = appDeployer();
		String deploymentId = deployer.deploy(request);
		AppStatus appStatus = deployer.status(deploymentId);
		assertTrue(appStatus.getInstances().size() > 0);
		for (Entry<String, AppInstanceStatus> instanceStatusEntry : appStatus.getInstances().entrySet()) {
			Map<String, String> attributes = instanceStatusEntry.getValue().getAttributes();
			assertFalse(attributes.containsKey("stdout"));
			assertFalse(attributes.containsKey("stderr"));
		}
		deployer.undeploy(deploymentId);
	}

	@Configuration
	@EnableConfigurationProperties(LocalDeployerProperties.class)
	public static class Config {

		@Bean
		public AppDeployer appDeployer(LocalDeployerProperties properties) {
			return new LocalAppDeployer(properties);
		}
	}

}
