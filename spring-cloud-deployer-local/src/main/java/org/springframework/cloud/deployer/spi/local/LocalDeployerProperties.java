/*
 * Copyright 2015-2018 the original author or authors.
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
import java.nio.file.Path;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;

/**
 * Configuration properties for the local deployer.
 *
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Ilayaperumal Gopinathan
 * @author Oleg Zhurakousky
 * @author Vinicius Carvalho
 */
@ConfigurationProperties(prefix = LocalDeployerProperties.PREFIX)
public class LocalDeployerProperties {

	/**
	 * Top level prefix for local deployer configuration properties.
	 */
	public static final String PREFIX = "spring.cloud.deployer.local";

	/**
	 * Deployer property allowing logging to be redirected to the output stream of the process that
	 * triggered child process.
	 * Could be set per the entire deployment (<em>i.e.</em> {@literal deployer.*.local.inheritLogging=true}) or per
	 * individual application (<em>i.e.</em> {@literal deployer.<app-name>.local.inheritLogging=true}).
	 */
	public static final String INHERIT_LOGGING = PREFIX + ".inheritLogging";

	/**
	 * Remote debugging property allowing one to specify port for the remote debug session.
	 * Must be set per individual application (<em>i.e.</em> {@literal deployer.<app-name>.local.debugPort=9999}).
	 */
	public static final String DEBUG_PORT = PREFIX + ".debugPort";

	/**
	 * Remote debugging property allowing one to specify if the startup of the application should
	 * be suspended until remote debug session is established. Values must be either 'y' or 'n'.
	 * Must be set per individual application (<em>i.e.</em> {@literal deployer.<app-name>.local.debugSuspend=y}).
	 */
	public static final String DEBUG_SUSPEND = PREFIX + ".debugSuspend";

	private static final Logger logger = LoggerFactory.getLogger(LocalDeployerProperties.class);

	private static final String JAVA_COMMAND = LocalDeployerUtils.isWindows() ? "java.exe" : "java";

	// looks like some windows systems uses 'Path' but processbuilder give it as 'PATH'
	private static final String[] ENV_VARS_TO_INHERIT_DEFAULTS_WIN = { "TMP", "TEMP", "PATH", "Path",
			AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON };

	private static final String[] ENV_VARS_TO_INHERIT_DEFAULTS_OTHER = { "TMP", "LANG", "LANGUAGE", "LC_.*", "PATH",
			AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON };

	/**
	 * Directory in which all created processes will run and create log files.
	 */
	private Path workingDirectoriesRoot = new File(System.getProperty("java.io.tmpdir")).toPath();

	/**
	 * Whether to delete created files and directories on JVM exit.
	 */
	private boolean deleteFilesOnExit = true;

	/**
	 * Array of regular expression patterns for environment variables that
	 * should be passed to launched applications.
	 */
	private String[] envVarsToInherit = LocalDeployerUtils.isWindows() ? ENV_VARS_TO_INHERIT_DEFAULTS_WIN
			: ENV_VARS_TO_INHERIT_DEFAULTS_OTHER;

	/**
	 * The command to run java.
	 */
	private String javaCmd = deduceJavaCommand();

	/**
	 * Maximum number of seconds to wait for application shutdown
	 * via the {@code /shutdown} endpoint.
	 */
	private int shutdownTimeout = 30;

	/**
	 * The Java Options to pass to the JVM, e.g -Dtest=foo
	 */
	private String javaOpts;

	/**
	 * Flag to indicate whether application properties are passed as command line args or in a
	 * SPRING_APPLICATION_JSON environment variable.  Default value is {@code true}.
	 */
	private boolean useSpringApplicationJson = true;

	/**
	 * The target percentag of free disk space to always aim for when cleaning downloaded
	 * resources (typically via the local maven repository). Specify as an integer greater
	 * than zero and less than 100. Default is 5.
	 */
	private int freeDiskSpacePercentage = 5;

	@Min(0)
	@Max(100)
	public int getFreeDiskSpacePercentage() {
		return freeDiskSpacePercentage;
	}

	public void setFreeDiskSpacePercentage(int freeDiskSpacePercentage) {
		this.freeDiskSpacePercentage = freeDiskSpacePercentage;
	}

	public String getJavaCmd() {
		return javaCmd;
	}

	public void setJavaCmd(String javaCmd) {
		this.javaCmd = javaCmd;
	}

	public Path getWorkingDirectoriesRoot() {
		return workingDirectoriesRoot;
	}

	public void setWorkingDirectoriesRoot(String workingDirectoriesRoot) {
		this.workingDirectoriesRoot = new File(workingDirectoriesRoot).toPath();
	}

	public boolean isDeleteFilesOnExit() {
		return deleteFilesOnExit;
	}

	public void setDeleteFilesOnExit(boolean deleteFilesOnExit) {
		this.deleteFilesOnExit = deleteFilesOnExit;
	}

	public String[] getEnvVarsToInherit() {
		return envVarsToInherit;
	}

	public void setEnvVarsToInherit(String[] envVarsToInherit) {
		this.envVarsToInherit = envVarsToInherit;
	}

	public int getShutdownTimeout() {
		return shutdownTimeout;
	}

	public LocalDeployerProperties setShutdownTimeout(int shutdownTimeout) {
		this.shutdownTimeout = shutdownTimeout;
		return this;
	}

	public String getJavaOpts() {
		return javaOpts;
	}

	public void setJavaOpts(String javaOpts) {
		this.javaOpts = javaOpts;
	}

	public boolean isUseSpringApplicationJson() {
		return useSpringApplicationJson;
	}

	public void setUseSpringApplicationJson(boolean useSpringApplicationJson) {
		this.useSpringApplicationJson = useSpringApplicationJson;
	}

	private String deduceJavaCommand() {
		String javaExecutablePath = JAVA_COMMAND;
		String javaHome = System.getProperty("java.home");
		if (javaHome != null) {
			File javaExecutable = new File(javaHome, "bin" + File.separator + javaExecutablePath);
			Assert.isTrue(javaExecutable.canExecute(),
					"Java executable'" + javaExecutable + "'discovered via 'java.home' system property '" + javaHome
							+ "' is not executable or does not exist.");
			javaExecutablePath = javaExecutable.getAbsolutePath();
		}
		else {
			logger.warn("System property 'java.home' is not set. Defaulting to the java executable path as "
					+ JAVA_COMMAND + " assuming it's in PATH.");
		}

		return javaExecutablePath;
	}

	@Override
	public String toString(){
		return new ToStringCreator(this)
				.append("workingDirectoriesRoot", this.workingDirectoriesRoot)
				.append("javaOpts", this.javaOpts)
				.append("envVarsToInherit", this.envVarsToInherit)
				.toString();
	}
}
