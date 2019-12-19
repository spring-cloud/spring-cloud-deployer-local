/*
 * Copyright 2016-2019 the original author or authors.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hamcrest.BaseMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
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
import org.springframework.util.FileSystemUtils;
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
 * @author Ilayaperumal Gopinathan
 */
@SpringBootTest(classes = {Config.class, AbstractIntegrationTests.Config.class}, value = {
		"maven.remoteRepositories.springRepo.url=https://repo.spring.io/libs-snapshot" })
public class LocalAppDeployerIntegrationTests extends AbstractAppDeployerIntegrationTests {

	private static final String TESTAPP_DOCKER_IMAGE_NAME = "springcloud/spring-cloud-deployer-spi-test-app:latest";

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
			return new DockerResource(TESTAPP_DOCKER_IMAGE_NAME);
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
			env = template.getForObject(url + "/actuator/env", String.class);
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
			// we're defaulting to SAJ so it's i.e.
			// instance.index not INSTANCE_INDEX
			assertThat(env, containsString("\"instance.index\""));
			assertThat(env, containsString("\"spring.application.index\""));
			assertThat(env, containsString("\"spring.cloud.application.guid\""));
			assertThat(env, containsString("\"spring.cloud.stream.instanceIndex\""));
		}
	}

	@Test
	public void testAppLogRetrieval() {
		Map<String, String> properties = new HashMap<>();
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Deploying {}...", request.getDefinition().getName());
		String deploymentId = appDeployer().deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));
		String logContent = appDeployer().getLog(deploymentId);
		assertThat(logContent, containsString("Starting DeployerIntegrationTestApplication"));
	}

	// TODO: remove when these two are forced in tck tests
	@Test
	public void testScale() {
		doTestScale(false);
	}

	@Test
	public void testScaleWithIndex() {
		doTestScale(true);
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
		if (resource instanceof DockerResource) {
			try {
				String containerId = getCommandOutput("docker ps -q --filter ancestor="+ TESTAPP_DOCKER_IMAGE_NAME);
				String logOutput = getCommandOutput("docker logs "+ containerId);
				assertTrue(logOutput.contains("Listening for transport dt_socket at address: 9999"));
			} catch (IOException e) {
			}
		}
		else {
			assertEquals("deploying", appStatus.toString());
		}

		deployer.undeploy(deploymentId);
	}

	@Test
	public void testInDebugModeWithSuspendedUseCamelCase() throws Exception {
		Map<String, String> properties = new HashMap<>();
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = testApplication();
		Map<String, String> deploymentProperties = new HashMap<>();
		deploymentProperties.put(LocalDeployerProperties.PREFIX + ".debugPort", "8888");
		deploymentProperties.put(LocalDeployerProperties.PREFIX + ".debugSuspend", "y");
		deploymentProperties.put(LocalDeployerProperties.PREFIX + ".inheritLogging", "true");
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, deploymentProperties);

		AppDeployer deployer = appDeployer();
		String deploymentId = deployer.deploy(request);
		Thread.sleep(5000);
		AppStatus appStatus = deployer.status(deploymentId);
		if (resource instanceof DockerResource) {
			try {
				String containerId = getCommandOutput("docker ps -q --filter ancestor="+ TESTAPP_DOCKER_IMAGE_NAME);
				String logOutput = getCommandOutput("docker logs "+ containerId);
				assertTrue(logOutput.contains("Listening for transport dt_socket at address: 8888"));
			} catch (IOException e) {
			}
		}
		else {
			assertEquals("deploying", appStatus.toString());
		}

		deployer.undeploy(deploymentId);
	}

	@Test
	public void testUseDefaultDeployerProperties()  throws IOException {

		LocalDeployerProperties localDeployerProperties = new LocalDeployerProperties();
		Path tmpPath = new File(System.getProperty("java.io.tmpdir")).toPath();
		Path customWorkDirRoot = tmpPath.resolve("test-default-directory");
		localDeployerProperties.setWorkingDirectoriesRoot(customWorkDirRoot.toFile().getAbsolutePath());

		// Create a new LocalAppDeployer using a working directory that is different from the default value.
		AppDeployer appDeployer = new LocalAppDeployer(localDeployerProperties);

		List<Path> beforeDirs = getBeforePaths(customWorkDirRoot);

		Map<String, String> properties = new HashMap<>();
		properties.put("server.port", "0");
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);


		// Deploy
		String deploymentId = appDeployer.deploy(request);
		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				appDeployer,
				Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));
		timeout = undeploymentTimeout();
		// Undeploy
		appDeployer.undeploy(deploymentId);
		assertThat(deploymentId, eventually(hasStatusThat(
				appDeployer,
				Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));

		List<Path> afterDirs = getAfterPaths(customWorkDirRoot);
		assertThat("Additional working directory not created", afterDirs.size(), CoreMatchers.is(beforeDirs.size()+1));

	}

	protected Matcher<String> hasStatusThat(final AppDeployer appDeployer, final Matcher<AppStatus> statusMatcher) {
		return new BaseMatcher<String>() {
			private AppStatus status;

			public boolean matches(Object item) {
				this.status = appDeployer.status((String)item);
				return statusMatcher.matches(this.status);
			}

			public void describeMismatch(Object item, Description mismatchDescription) {
				mismatchDescription.appendText("status of ").appendValue(item).appendText(" ");
				statusMatcher.describeMismatch(this.status, mismatchDescription);
			}

			public void describeTo(Description description) {
				statusMatcher.describeTo(description);
			}
		};
	}

	@Test
	public void testZeroPortReportsDeployed() throws IOException {
		Map<String, String> properties = new HashMap<>();
		properties.put("server.port", "0");
		Path tmpPath = new File(System.getProperty("java.io.tmpdir")).toPath();
		Path customWorkDirRoot = tmpPath.resolve("spring-cloud-deployer-app-workdir");
		Map<String, String> deploymentProperties = new HashMap<>();
		deploymentProperties.put(LocalDeployerProperties.PREFIX + ".working-directories-root", customWorkDirRoot.toFile().getAbsolutePath());

		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, deploymentProperties);

		List<Path> beforeDirs = getBeforePaths(customWorkDirRoot);

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


		List<Path> afterDirs = getAfterPaths(customWorkDirRoot);
		assertThat("Additional working directory not created", afterDirs.size(), CoreMatchers.is(beforeDirs.size()+1));
	}

	private List<Path> getAfterPaths(Path customWorkDirRoot) throws IOException {
		if (!Files.exists(customWorkDirRoot)) {
			return new ArrayList<>();
		}
		return Files.walk(customWorkDirRoot, 1)
					.filter(path -> Files.isDirectory(path))
					.filter(path -> !path.getFileName().toString().startsWith("."))
					.collect(Collectors.toList());
	}

	private List<Path> getBeforePaths(Path customWorkDirRoot) throws IOException {
		List<Path> beforeDirs = new ArrayList<>();
		beforeDirs.add(customWorkDirRoot);
		if (Files.exists(customWorkDirRoot)) {
			beforeDirs = Files.walk(customWorkDirRoot, 1)
					.filter(path -> Files.isDirectory(path))
					.collect(Collectors.toList());
		}
		return beforeDirs;
	}

	private String getCommandOutput(String cmd) throws IOException {
		Process process = Runtime.getRuntime().exec(cmd);
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
		return stdInput.lines().findFirst().get();
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
