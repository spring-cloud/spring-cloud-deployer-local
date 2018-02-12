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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.util.RuntimeVersionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

/**
 * Base class for local app deployer and task launcher providing
 * support for common functionality.
 *
 * @author Janne Valkealahti
 * @author Mark Fisher
 * @author Ilayaperumal Gopinathan
 * @author Thomas Risberg
 * @author Oleg Zhurakousky
 * @author Vinicius Carvalho
 */
public abstract class AbstractLocalDeployerSupport {

	public static final String USE_SPRING_APPLICATION_JSON_KEY =
			LocalDeployerProperties.PREFIX + ".use-spring-application-json";

	public static final String SPRING_APPLICATION_JSON = "SPRING_APPLICATION_JSON";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private final LocalDeployerProperties properties;

	private final RestTemplate restTemplate = new RestTemplate();

	private final JavaCommandBuilder javaCommandBuilder;

	private final DockerCommandBuilder dockerCommandBuilder;

	private String[] envVarsSetByDeployer =
			{"SPRING_CLOUD_APPLICATION_GUID", "SPRING_APPLICATION_INDEX", "INSTANCE_INDEX"};

	/**
	 * Instantiates a new abstract deployer support.
	 *
	 * @param properties the local deployer properties
	 */
	public AbstractLocalDeployerSupport(LocalDeployerProperties properties) {
		Assert.notNull(properties, "LocalDeployerProperties must not be null");
		this.properties = properties;
		this.javaCommandBuilder = new JavaCommandBuilder(properties);
		this.dockerCommandBuilder = new DockerCommandBuilder();
	}
	
	protected String buildRemoteDebugInstruction(Map<String, String> deploymentProperties, String deploymentId,
			int instanceIndex, int port) {
		String ds = deploymentProperties.getOrDefault(LocalDeployerProperties.DEBUG_SUSPEND, "y");
		StringBuilder debugCommandBuilder = new StringBuilder();
		String debugCommand;
		logger.warn("Deploying app with deploymentId {}, instance {}. Remote debugging is enabled on port {}.",
				deploymentId, instanceIndex, port);
		debugCommandBuilder.append("-agentlib:jdwp=transport=dt_socket,server=y,suspend=");
		debugCommandBuilder.append(ds.trim());
		debugCommandBuilder.append(",address=");
		debugCommandBuilder.append(port);
		debugCommand = debugCommandBuilder.toString();
		logger.debug("Deploying app with deploymentId {}, instance {}.  Debug Command = [{}]", debugCommand);
		if (ds.equals("y")) {
			logger.warn("Deploying app with deploymentId {}.  Application Startup will be suspended until remote "
					+ "debugging session is established.");
		}

		return debugCommand;
	}

	/**
	 * Create the RuntimeEnvironmentInfo.
	 *
	 * @return the local runtime environment info
	 */
	protected RuntimeEnvironmentInfo createRuntimeEnvironmentInfo(Class<?> spiClass, Class<?> implementationClass) {
		return new RuntimeEnvironmentInfo.Builder()
				.spiClass(spiClass)
				.implementationName(implementationClass.getSimpleName())
				.implementationVersion(RuntimeVersionUtils.getVersion(implementationClass))
				.platformType("Local")
				.platformApiVersion(System.getProperty("os.name") + " " + System.getProperty("os.version"))
				.platformClientVersion(System.getProperty("os.version"))
				.platformHostVersion(System.getProperty("os.version"))
				.build();
	}

	/**
	 * Gets the local deployer properties.
	 *
	 * @return the local deployer properties
	 */
	final protected LocalDeployerProperties getLocalDeployerProperties() {
		return properties;
	}

	/**
	 * Retain the environment variable strings in the provided set indicated by
	 * {@link LocalDeployerProperties#getEnvVarsToInherit}.
	 * This assumes that the provided set can be modified.
	 *
	 * @param vars set of environment variable strings
	 */
	protected void retainEnvVars(Set<String> vars) {

		List<String> patterns = new ArrayList<>(Arrays.asList(getLocalDeployerProperties().getEnvVarsToInherit()));
		patterns.addAll(Arrays.asList(this.envVarsSetByDeployer));

		for (Iterator<String> iterator = vars.iterator(); iterator.hasNext();) {
			String var = iterator.next();
			boolean retain = false;
			for (String pattern : patterns) {
				if (Pattern.matches(pattern, var)) {
					retain = true;
					break;
				}
			}
			if (!retain) {
				iterator.remove();
			}
		}
	}

	/**
	 * Builds the process builder.
	 *
	 * @param request the request
	 * @param appInstanceEnv the instance environment variables
	 * @param appProperties the app properties
	 * @return the process builder
	 */
	protected ProcessBuilder buildProcessBuilder(AppDeploymentRequest request, Map<String, String> appInstanceEnv,
												 Map<String, String> appProperties, Optional<Integer> appInstanceNumber, String deploymentId) {
		Assert.notNull(request, "AppDeploymentRequest must be set");
		Assert.notNull(appProperties, "Args must be set");
		String[] commands = null;
		Map<String, String> appInstanceEnvToUse = new HashMap<>(appInstanceEnv);
		Map<String, String> appPropertiesToUse = new HashMap<>();
		handleAppPropertiesPassing(request, appProperties, appInstanceEnvToUse, appPropertiesToUse);
		if (request.getResource() instanceof DockerResource) {
			commands = this.dockerCommandBuilder.buildExecutionCommand(request,
					appInstanceEnvToUse, appPropertiesToUse, appInstanceNumber);
		}
		else {
			commands = this.javaCommandBuilder.buildExecutionCommand(request,
					appInstanceEnvToUse, appPropertiesToUse, appInstanceNumber);
		}

		// tweak escaping double quotes needed for windows
		if (LocalDeployerUtils.isWindows()) {
			for (int i = 0; i < commands.length; i++) {
				commands[i] = commands[i].replace("\"", "\\\"");
			}
		}

		ProcessBuilder builder = new ProcessBuilder(commands);
		if (!(request.getResource() instanceof DockerResource)) {
			builder.environment().putAll(appInstanceEnv);
			builder.environment().putAll(appInstanceEnvToUse);
		}
		retainEnvVars(builder.environment().keySet());
		
		if (this.containsValidDebugPort(request.getDeploymentProperties(), deploymentId)) {
			int portToUse = calculateDebugPort(request.getDeploymentProperties(), appInstanceNumber.orElseGet(() -> 0));
			builder.command().add(1, this.buildRemoteDebugInstruction(
					request.getDeploymentProperties(),
					deploymentId,
					appInstanceNumber.orElseGet(() -> 0),
					portToUse));
		}
		
		return builder;
	}

	protected void handleAppPropertiesPassing(AppDeploymentRequest request, Map<String, String> appProperties,
											  Map<String, String> appInstanceEnvToUse,
											  Map<String, String> appPropertiesToUse) {
		if (useSpringApplicationJson(request)) {
			try {
				//If SPRING_APPLICATION_JSON is found, explode it and merge back into appProperties
				Map<String, String> localApplicationProperties = new HashMap<>(appProperties);
				if(localApplicationProperties.containsKey(SPRING_APPLICATION_JSON)){
					localApplicationProperties.putAll(OBJECT_MAPPER.readValue(appProperties.get(SPRING_APPLICATION_JSON), new TypeReference<HashMap<String,Object>>() {}));
					localApplicationProperties.remove(SPRING_APPLICATION_JSON);
				}
				appInstanceEnvToUse.putAll(Collections.singletonMap(
						SPRING_APPLICATION_JSON, OBJECT_MAPPER.writeValueAsString(localApplicationProperties)));
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		else {
			appPropertiesToUse.putAll(appProperties);
		}
	}

	/**
	 * Shut down the {@link Process} backing the application {@link Instance}.
	 * If the application exposes a {@code /shutdown} endpoint, that will be
	 * invoked followed by a wait that will not exceed the number of seconds
	 * indicated by {@link LocalDeployerProperties#shutdownTimeout}. If the
	 * timeout period is exceeded (or if the {@code /shutdown} endpoint is not exposed),
	 * the process will be shut down via {@link Process#destroy()}.
	 *
	 * @param instance the application instance to shut down
	 */
	protected void shutdownAndWait(Instance instance) {
		try {
			int timeout = getLocalDeployerProperties().getShutdownTimeout();
			if (timeout > 0) {
				ResponseEntity<String> response = restTemplate.postForEntity(
						instance.getBaseUrl() + "/shutdown", null, String.class);
				if (response.getStatusCode().is2xxSuccessful()) {
					long timeoutTimestamp = System.currentTimeMillis() + (timeout * 1000);
					while (isAlive(instance.getProcess()) && System.currentTimeMillis() < timeoutTimestamp) {
						Thread.sleep(1000);
					}
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		catch (Exception e) {
			// ignore all other errors as we're going to
			// destroy process if it's alive
		}
		finally {
			if (isAlive(instance.getProcess())) {
				instance.getProcess().destroy();
			}
		}
	}

	// Copy-pasting of JDK8+ isAlive method to retain JDK7 compatibility
	protected static boolean isAlive(Process process) {
		try {
			process.exitValue();
			return false;
		}
		catch (IllegalThreadStateException e) {
			return true;
		}
	}

	/**
	 * Determines if there is a valid debug port specified in the deployment properites.
	 * @param deploymentProperties the deployment properties to validate
	 * @param deploymentId the deployment Id for logging purposes
	 * @return true if there is a valid debug port, false otherwise
	 */
	protected boolean containsValidDebugPort(Map<String, String> deploymentProperties, String deploymentId) {
		boolean validDebugPort = false;
		if (deploymentProperties.containsKey(LocalDeployerProperties.DEBUG_PORT)) {
			String basePort = deploymentProperties.get(LocalDeployerProperties.DEBUG_PORT);
			try {
				int port = Integer.parseInt(basePort);
				if (port <= 0) {
					logger.error("The debug port {} specified for deploymentId {} must be greater than zero");
					return false;
				}
			} catch (NumberFormatException e) {
				logger.error("The debug port {} specified for deploymentId {} can not be parsed to an integer.",
						basePort, deploymentId);
				return false;
			}
			validDebugPort = true;
		}
		return validDebugPort;
	}

	/**
	 * Gets the base debug port value and adds the instance count.  Assumes {@link #containsValidDebugPort(Map, String)}
	 * has been called before to validate the deployment properties.
	 * @param deploymentProperties deployment properties with a valid value of debug port
	 * @param instanceIndex the index of the application to deploy
	 * @return the value of adding the debug port + instance index.
	 */
	protected int calculateDebugPort(Map<String, String> deploymentProperties, int instanceIndex) {
		String basePort = deploymentProperties.get(LocalDeployerProperties.DEBUG_PORT);
		return Integer.parseInt(basePort) + instanceIndex;
	}
	
	private boolean useSpringApplicationJson(AppDeploymentRequest request) {
		return Optional.ofNullable(request.getDeploymentProperties().get(USE_SPRING_APPLICATION_JSON_KEY))
				.map(Boolean::valueOf)
				.orElse(this.properties.isUseSpringApplicationJson());
	}

	protected interface Instance {

		URL getBaseUrl();

		Process getProcess();
	}
}
