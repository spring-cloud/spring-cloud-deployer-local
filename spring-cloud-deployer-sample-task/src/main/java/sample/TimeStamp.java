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

package sample;

import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.local.LocalDeployerProperties;
import org.springframework.cloud.deployer.spi.local.LocalTaskLauncher;

/**
 * @author Janne Valkealahti
 * @author Mark Fisher
 */
public class TimeStamp {

	public static void main(String[] args) throws InterruptedException {
		LocalTaskLauncher launcher = new LocalTaskLauncher(new LocalDeployerProperties());
		String timestampId = launcher.launch(createAppDeploymentRequest("timestamp-task"));
		for (int i = 0; i < 50; i++) {
			Thread.sleep(100);
			System.out.println("timestamp: " + launcher.status(timestampId));
		}
		// timestamp completes quickly, but we can 'cancel' the running task
		launcher.cancel(timestampId);
		System.out.println("timestamp after cancel: " + launcher.status(timestampId));
	}

	private static AppDeploymentRequest createAppDeploymentRequest(String app) {
		MavenResource resource = new MavenResource.Builder()
				.artifactId(app)
				.groupId("org.springframework.cloud.task.app")
				.version("1.0.0.BUILD-SNAPSHOT")
				.build();
		Map<String, String> properties = new HashMap<>();
		properties.put("server.port", "0");
		AppDefinition definition = new AppDefinition(app, properties);
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);
		return request;
	}
}
