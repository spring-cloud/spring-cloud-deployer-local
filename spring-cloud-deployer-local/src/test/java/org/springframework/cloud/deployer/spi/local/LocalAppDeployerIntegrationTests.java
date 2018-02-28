/*
 * Copyright 2016-2018 the original author or authors.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
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
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.deployed;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.unknown;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

/**
 * Integration tests for {@link LocalAppDeployer}.
 *
 * Now supports running with Docker images for tests, just set this env var:
 *
 *   SPRING_CLOUD_DEPLOYER_SPI_TEST_USE_DOCKER=true
 *
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Janne Valkealahti
 */
@SpringBootTest(classes = {Config.class, AbstractIntegrationTests.Config.class}, value = {
		"maven.remoteRepositories.springRepo.url=https://repo.spring.io/libs-snapshot" })
public class LocalAppDeployerIntegrationTests extends AbstractAppDeployerIntegrationTests {

	@Autowired
	private AppDeployer appDeployer;

	@Value("${spring-cloud-deployer-spi-test-use-docker:false}")
	private boolean useDocker;

	@Override
	protected AppDeployer provideAppDeployer() {
		return appDeployer;
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
	public void testEnvVariablesInheritedViaEnvEndpoint() {
		if (useDocker) {
			// would not expect to be able to check anything on docker
			return;
		}
		Map<String, String> properties = new HashMap<>();
		properties.put("management.security.enabled", "false");
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying {}...", request.getDefinition().getName());

		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

		Map<String, AppInstanceStatus> instances = appDeployer().status(deploymentId).getInstances();
		String url = null;
		if (instances.size() == 1) {
			url = instances.entrySet().iterator().next().getValue().getAttributes().get("url");
		}
		String env = null;
		if (url != null) {
			RestTemplate template = new RestTemplate();
			env = template.getForObject(url + "/env", String.class);
		}

		log.info("Undeploying {}...", deploymentId);

		timeout = undeploymentTimeout();
		appDeployer().undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));

		assertThat(url, notNullValue());
		if (LocalDeployerUtils.isWindows()) {
			// windows is weird, we may still get Path or PATH
			assertThat(env, anyOf(containsString("\"Path\""), containsString("\"PATH\"")));
		}
		else {
			assertThat(env, containsString("\"PATH\""));
		}
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

	@Test
	public void testInDebugModeWithSuspended() throws Exception {
		Map<String, String> properties = new HashMap<>();
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = testApplication();
		Map<String, String> deploymentProperties = new HashMap<>();
		deploymentProperties.put(LocalDeployerProperties.DEBUG_PORT, "9999");
		deploymentProperties.put(LocalDeployerProperties.DEBUG_SUSPEND, "y");
		deploymentProperties.put(LocalDeployerProperties.INHERIT_LOGGING, "true");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, deploymentProperties);

		AppDeployer deployer = appDeployer();
		String deploymentId = deployer.deploy(request);
		Thread.sleep(5000);
		AppStatus appStatus = deployer.status(deploymentId);
		assertEquals("deploying", appStatus.toString());
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
