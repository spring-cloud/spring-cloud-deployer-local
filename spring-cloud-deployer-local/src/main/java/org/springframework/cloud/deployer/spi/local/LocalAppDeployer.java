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

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.util.StringUtils;

/**
 * An {@link AppDeployer} implementation that spins off a new JVM process per app instance.
 *
 * @author Eric Bottard
 * @author Marius Bogoevici
 * @author Mark Fisher
 * @author Ilayaperumal Gopinathan
 * @author Janne Valkealahti
 * @author Patrick Peralta
 * @author Thomas Risberg
 * @author Oleg Zhurakousky
 * @author Michael Minella
 */
public class LocalAppDeployer extends AbstractLocalDeployerSupport implements AppDeployer {

	private Path logPathRoot;

	private static final Logger logger = LoggerFactory.getLogger(LocalAppDeployer.class);

	static final String SERVER_PORT_KEY = "server.port";

	private static final String JMX_DEFAULT_DOMAIN_KEY = "spring.jmx.default-domain";

	private static final String ENDPOINTS_SHUTDOWN_ENABLED_KEY = "endpoints.shutdown.enabled";

	private final Map<String, List<AppInstance>> running = new ConcurrentHashMap<>();

	/**
	 * Instantiates a new local app deployer.
	 *
	 * @param properties the properties
	 */
	public LocalAppDeployer(LocalDeployerProperties properties) {
		super(properties);
		try {
			this.logPathRoot = Files.createTempDirectory(properties.getWorkingDirectoriesRoot(), "spring-cloud-dataflow-");
		}
		catch (IOException e) {
			throw new RuntimeException("Could not create workdir root: " + properties.getWorkingDirectoriesRoot(), e);
		}
	}

	@Override
	public String deploy(AppDeploymentRequest request) {

		String group = request.getDeploymentProperties().get(GROUP_PROPERTY_KEY);
		String deploymentId = String.format("%s.%s", group, request.getDefinition().getName());

		validateStatus(deploymentId);

		List<AppInstance> processes = new ArrayList<>();
		running.put(deploymentId, processes);

		boolean useDynamicPort = !request.getDefinition().getProperties().containsKey(SERVER_PORT_KEY);

		// consolidatedAppProperties is a Map of all application properties to be used by
		// the app being launched.  These values should end up as environment variables
		// either explicitly or as a SPRING_APPLICATION_JSON value.
		HashMap<String, String> consolidatedAppProperties = new HashMap<>(request.getDefinition().getProperties());

		consolidatedAppProperties.put(JMX_DEFAULT_DOMAIN_KEY, deploymentId);

		if (!request.getDefinition().getProperties().containsKey(ENDPOINTS_SHUTDOWN_ENABLED_KEY)) {
			consolidatedAppProperties.put(ENDPOINTS_SHUTDOWN_ENABLED_KEY, "true");
		}

		consolidatedAppProperties.put("endpoints.jmx.unique-names", "true");

		if (group != null) {
			consolidatedAppProperties.put("spring.cloud.application.group", group);
		}

		try {
			Path deploymentGroupDir = createLogDir(group);

			Path workDir = createWorkingDir(deploymentId, deploymentGroupDir);

			String countProperty = request.getDeploymentProperties().get(COUNT_PROPERTY_KEY);
			int count = (StringUtils.hasText(countProperty)) ? Integer.parseInt(countProperty) : 1;

			for (int i = 0; i < count; i++) {

				// This Map is the consolidated application properties *for the instance*
				// to be deployed in this iteration
				Map<String, String> appInstanceEnv = new HashMap<>(consolidatedAppProperties);

				int port = calcServerPort(request, useDynamicPort, appInstanceEnv);

				appInstanceEnv.put("INSTANCE_INDEX", Integer.toString(i));
				appInstanceEnv.put("SPRING_APPLICATION_INDEX", Integer.toString(i));
				appInstanceEnv.put("SPRING_CLOUD_APPLICATION_GUID", Integer.toString(port));

				AppInstance instance = new AppInstance(deploymentId, i, port);

				ProcessBuilder builder = buildProcessBuilder(request, appInstanceEnv, Optional.of(i), deploymentId).inheritIO();

				builder.directory(workDir.toFile());

				if (this.shouldInheritLogging(request)){
					instance.start(builder, workDir);
					logger.info("Deploying app with deploymentId {} instance {}.\n   Logs will be inherited.", deploymentId, i);
				}
				else {
					instance.start(builder, workDir, getLocalDeployerProperties().isDeleteFilesOnExit());
					logger.info("Deploying app with deploymentId {} instance {}.\n   Logs will be in {}", deploymentId, i, workDir);
				}

				processes.add(instance);
			}
		}
		catch (IOException e) {
			throw new RuntimeException("Exception trying to deploy " + request, e);
		}
		return deploymentId;
	}

	@Override
	public void undeploy(String id) {
		List<AppInstance> processes = running.get(id);
		if (processes != null) {
			for (AppInstance instance : processes) {
				if (isAlive(instance.getProcess())) {
					logger.info("Un-deploying app with deploymentId {} instance {}.", id, instance.getInstanceNumber());
					shutdownAndWait(instance);
				}
			}
			running.remove(id);
		}
		else {
			throw new IllegalStateException(String.format("App with deploymentId %s is not in a deployed state.", id));
		}
	}

	@Override
	public AppStatus status(String id) {
		List<AppInstance> instances = running.get(id);
		AppStatus.Builder builder = AppStatus.of(id);

		if (instances != null) {
			for (AppInstance instance : instances) {
				builder.with(instance);
			}
		}

		return builder.build();
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return super.createRuntimeEnvironmentInfo(AppDeployer.class, this.getClass());
	}

	@PreDestroy
	public void shutdown() throws Exception {
		for (String deploymentId : running.keySet()) {
			undeploy(deploymentId);
		}
	}

	private Path createWorkingDir(String deploymentId, Path deploymentGroupDir) throws IOException {
		Path workDir = Files
				.createDirectory(Paths.get(deploymentGroupDir.toFile().getAbsolutePath(), deploymentId));
		if (getLocalDeployerProperties().isDeleteFilesOnExit()) {
			workDir.toFile().deleteOnExit();
		}
		return workDir;
	}

	private Path createLogDir(String group) throws IOException {
		Path deploymentGroupDir = Paths.get(logPathRoot.toFile().getAbsolutePath(),
				group + "-" + System.currentTimeMillis());
		if (!Files.exists(deploymentGroupDir)) {
			Files.createDirectory(deploymentGroupDir);
//			deploymentGroupDir.toFile().deleteOnExit();
		}
		return deploymentGroupDir;
	}

	private void validateStatus(String deploymentId) {
		DeploymentState state = status(deploymentId).getState();

		if (state != DeploymentState.unknown) {
			throw new IllegalStateException(String.format("App with deploymentId [%s] is already deployed with state [%s]",
					deploymentId, state));
		}
	}

	/**
	 * Will check if {@link LocalDeployerProperties#INHERIT_LOGGING} is set by
	 * checking deployment properties.
	 */
	private boolean shouldInheritLogging(AppDeploymentRequest request){
		boolean inheritLogging = false;
		if (request.getDeploymentProperties().containsKey(LocalDeployerProperties.INHERIT_LOGGING)){
			inheritLogging = Boolean.parseBoolean(request.getDeploymentProperties().get(LocalDeployerProperties.INHERIT_LOGGING));
		}
		return inheritLogging;
	}

	private static class AppInstance implements Instance, AppInstanceStatus {

		private final String deploymentId;

		private final int instanceNumber;

		private final URL baseUrl;

		private int pid;

		private Process process;

		private File workFile;

		private File stdout;

		private File stderr;

		private final Map<String, String> attributes = new TreeMap<>();

		private AppInstance(String deploymentId, int instanceNumber, int port) throws IOException {
			this.deploymentId = deploymentId;
			this.instanceNumber = instanceNumber;
			attributes.put("port", Integer.toString(port));
			attributes.put("guid", Integer.toString(port));
			this.baseUrl = new URL("http", Inet4Address.getLocalHost().getHostAddress(), port, "");
			attributes.put("url", baseUrl.toString());
		}

		@Override
		public String getId() {
			return deploymentId + "-" + instanceNumber;
		}

		@Override
		public URL getBaseUrl() {
			return this.baseUrl;
		}

		@Override
		public Process getProcess() {
			return this.process;
		}

		@Override
		public String toString() {
			return String.format("%s [%s]", getId(), getState());
		}

		@Override
		public DeploymentState getState() {
			Integer exit = getProcessExitValue(process);
			// TODO: consider using exit code mapper concept from batch
			if (exit != null) {
				return DeploymentState.failed;
			}
			try {
				HttpURLConnection urlConnection = (HttpURLConnection) baseUrl.openConnection();
				urlConnection.setConnectTimeout(100);
				urlConnection.connect();
				urlConnection.disconnect();
				return DeploymentState.deployed;
			}
			catch (IOException e) {
				return DeploymentState.deploying;
			}
		}

		public int getInstanceNumber() {
			return instanceNumber;
		}

		@Override
		public Map<String, String> getAttributes() {
			return this.attributes;
		}

		/**
		 * Will start the process while redirecting 'out' and 'err' streams
		 * to the 'out' and 'err' streams of this process.
		 */
		private void start(ProcessBuilder builder, Path workDir) throws IOException {
			this.workFile = workDir.toFile();
			this.attributes.put("working.dir", this.workFile.getAbsolutePath());
			this.process = builder.start();
		    this.pid = getLocalProcessPid(this.process);
		    if (pid > 0) {
				// add pid if we got it
				attributes.put("pid", Integer.toString(pid));
			}
		}

		private void start(ProcessBuilder builder, Path workDir, boolean deleteOnExist) throws IOException {
			String workDirPath = workDir.toFile().getAbsolutePath();

			this.stdout = Files.createFile(Paths.get(workDirPath, "stdout_" + instanceNumber + ".log")).toFile();
			this.attributes.put("stdout", stdout.getAbsolutePath());

			this.stderr = Files.createFile(Paths.get(workDirPath, "stderr_" + instanceNumber + ".log")).toFile();
			this.attributes.put("stderr", stderr.getAbsolutePath());

			if (deleteOnExist) {
				this.stdout.deleteOnExit();
				this.stderr.deleteOnExit();
			}
			builder.redirectOutput(Redirect.to(this.stdout));
			builder.redirectError(Redirect.to(this.stderr));

			this.start(builder, workDir);
		}
	}

	/**
	 * Returns the process exit value. We explicitly use Integer instead of int
	 * to indicate that if {@code NULL} is returned, the process is still running.
	 * @param process the process
	 * @return the process exit value or {@code NULL} if process is still alive
	 */
	private static Integer getProcessExitValue(Process process) {
		try {
			return process.exitValue();
		}
		catch (IllegalThreadStateException e) {
			// process is still alive
			return null;
		}
	}

	/**
	 * Gets the local process pid if available. This should be a safe workaround
	 * for unix systems where reflection can be used to get pid. More reliable
	 * way should land with jdk9.
	 *
	 * @param p the process
	 * @return the local process pid
	 */
	private static synchronized int getLocalProcessPid(Process p) {
		int pid = 0;
		try {
			if (p.getClass().getName().equals("java.lang.UNIXProcess")) {
				Field f = p.getClass().getDeclaredField("pid");
				f.setAccessible(true);
				pid = f.getInt(p);
				f.setAccessible(false);
			}
		} catch (Exception e) {
			pid = 0;
		}
		return pid;
	}

}
