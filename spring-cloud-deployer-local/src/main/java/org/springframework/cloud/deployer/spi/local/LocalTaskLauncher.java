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
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;

/**
 * A {@link TaskLauncher} implementation that spins off a new JVM process per task launch.
 *
 * @author Eric Bottard
 * @author Marius Bogoevici
 * @author Mark Fisher
 * @author Janne Valkealahti
 * @author Thomas Risberg
 * @author Oleg Zhurakousky
 * @author Michael Minella
 */
public class LocalTaskLauncher extends AbstractLocalDeployerSupport implements TaskLauncher {

	private Path logPathRoot;

	private static final Logger logger = LoggerFactory.getLogger(LocalTaskLauncher.class);

	private static final String SERVER_PORT_KEY = "server.port";

	private final String SERVER_PORT_KEY_PREFIX = "--" + SERVER_PORT_KEY + "=";

	private static final String JMX_DEFAULT_DOMAIN_KEY = "spring.jmx.default-domain";

	private final Map<String, TaskInstance> running = new ConcurrentHashMap<>();

	/**
	 * Instantiates a new local task launcher.
	 *
	 * @param properties the properties
	 */
	public LocalTaskLauncher(LocalDeployerProperties properties) {
		super(properties);
	}

	@Override
	public String launch(AppDeploymentRequest request) {

		String taskLaunchId = request.getDefinition().getName() + "-" + UUID.randomUUID().toString();

		HashMap<String, String> args = new HashMap<>();
		args.putAll(request.getDefinition().getProperties());
		args.put(JMX_DEFAULT_DOMAIN_KEY, taskLaunchId);
		args.put("endpoints.shutdown.enabled", "true");
		args.put("endpoints.jmx.unique-names", "true");

		try {

			Path dir = createLogDir(request);

			Path workDir = createWorkingDir(taskLaunchId, dir);

			boolean useDynamicPort = isDynamicPort(request);

			int port = calcServerPort(request, useDynamicPort, args);

			ProcessBuilder builder = buildProcessBuilder(request, args, Optional.empty(), taskLaunchId);

			TaskInstance instance = new TaskInstance(builder, workDir, port);

			running.put(taskLaunchId, instance);

			if (getLocalDeployerProperties().isDeleteFilesOnExit()) {
				instance.stdout.deleteOnExit();
				instance.stderr.deleteOnExit();
			}

			logger.info("launching task {}\n   Logs will be in {}", taskLaunchId, workDir);
		}
		catch (IOException e) {
			throw new RuntimeException("Exception trying to launch " + request, e);
		}

		return taskLaunchId;
	}

	private boolean isDynamicPort(AppDeploymentRequest request) {
		boolean isServerPortKeyonArgs = isServerPortKeyPresentOnArgs(request);
		return !request.getDefinition().getProperties().containsKey(SERVER_PORT_KEY)
				&& !isServerPortKeyonArgs;
	}

	@Override
	public void cancel(String id) {
		TaskInstance instance = running.get(id);
		if (instance != null) {
			instance.cancelled = true;
			if (isAlive(instance.getProcess())) {
				shutdownAndWait(instance);
			}
		}
	}

	@Override
	public TaskStatus status(String id) {
		TaskInstance instance = running.get(id);
		if (instance != null) {
			return new TaskStatus(id, instance.getState(), instance.getAttributes());
		}
		return new TaskStatus(id, LaunchState.unknown, null);
	}

	@Override
	public void cleanup(String id) {
	}

	@Override
	public void destroy(String appName) {
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return super.createRuntimeEnvironmentInfo(TaskLauncher.class, this.getClass());
	}

	@PreDestroy
	public void shutdown() throws Exception {
		for (String taskLaunchId : running.keySet()) {
			cancel(taskLaunchId);
		}
	}

	private Path createWorkingDir(String taskLaunchId, Path dir) throws IOException {

		Path workDir = Files.createDirectory(Paths.get(dir.toFile().getAbsolutePath(),
				taskLaunchId));

		if (getLocalDeployerProperties().isDeleteFilesOnExit()) {
			workDir.toFile().deleteOnExit();
		}

		return workDir;
	}

	private Path createLogDir(AppDeploymentRequest request) throws IOException {

		if (this.logPathRoot == null) {
			this.logPathRoot =
					Files.createTempDirectory(getLocalDeployerProperties().getWorkingDirectoriesRoot(),
							request.getDefinition().getName());
		}

		String qualifiedName = request.getDefinition().getName() + "-" + System.currentTimeMillis();

		Path dir = Paths.get(logPathRoot.toFile().getAbsolutePath(), qualifiedName);

		if (!Files.exists(dir)) {
			Files.createDirectory(dir);
			dir.toFile().deleteOnExit();
		}

		return dir;
	}

	private static class TaskInstance implements Instance {

		private final Process process;

		private final File workDir;

		private final File stdout;

		private final File stderr;

		private final URL baseUrl;

		private boolean cancelled;

		private TaskInstance(ProcessBuilder builder, Path workDir, int port) throws IOException {
			builder.directory(workDir.toFile());
			String workDirPath = workDir.toFile().getAbsolutePath();
			this.stdout = Files.createFile(Paths.get(workDirPath, "stdout.log")).toFile();
			this.stderr = Files.createFile(Paths.get(workDirPath, "stderr.log")).toFile();
			builder.redirectOutput(this.stdout);
			builder.redirectError(this.stderr);
			this.process = builder.start();
			this.workDir = workDir.toFile();
			this.baseUrl = new URL("http", Inet4Address.getLocalHost().getHostAddress(), port, "");
		}

		@Override
		public URL getBaseUrl() {
			return this.baseUrl;
		}

		@Override
		public Process getProcess() {
			return this.process;
		}

		public LaunchState getState() {
			if (cancelled) {
				return LaunchState.cancelled;
			}
			Integer exit = getProcessExitValue(process);
			// TODO: consider using exit code mapper concept from batch
			if (exit != null) {
				if (exit == 0) {
					return LaunchState.complete;
				}
				else {
					return LaunchState.failed;
				}
			}
			try {
				HttpURLConnection urlConnection = (HttpURLConnection) baseUrl.openConnection();
				urlConnection.setConnectTimeout(100);
				urlConnection.connect();
				urlConnection.disconnect();
				return LaunchState.running;
			}
			catch (IOException e) {
				return LaunchState.launching;
			}
		}

		private Map<String, String> getAttributes() {
			HashMap<String, String> result = new HashMap<>();
			result.put("working.dir", workDir.getAbsolutePath());
			result.put("stdout", stdout.getAbsolutePath());
			result.put("stderr", stderr.getAbsolutePath());
			result.put("url", baseUrl.toString());
			return result;
		}
	}

	/**
	 * Returns the process exit value. We explicitly use Integer instead of int
	 * to indicate that if {@code NULL} is returned, the process is still running.
	 *
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

	private boolean isServerPortKeyPresentOnArgs(AppDeploymentRequest request) {
		boolean result = false;
			for (String argument : request.getCommandlineArguments()) {
				if (argument.startsWith(SERVER_PORT_KEY_PREFIX)) {
					result = true;
					break;
				}
		}
		return result;
	}
}
