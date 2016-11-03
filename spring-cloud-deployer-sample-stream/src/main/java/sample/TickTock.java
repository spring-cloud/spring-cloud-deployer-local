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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.local.LocalAppDeployer;
import org.springframework.cloud.deployer.spi.local.LocalDeployerProperties;

/**
 * @author Mark Fisher
 */
public class TickTock {

	public static void main(String[] args) throws InterruptedException {
		LocalAppDeployer deployer = new LocalAppDeployer(new LocalDeployerProperties());
		String logId = deployer.deploy(createAppDeploymentRequest("log-sink-kafka", "ticktock"));
		String timeId = deployer.deploy(createAppDeploymentRequest("time-source-kafka", "ticktock"));
		for (int i = 0; i < 12; i++) {
			Thread.sleep(5 * 1000);
			System.out.println("time: " + deployer.status(timeId));
			System.out.println("log:  " + deployer.status(logId));
		}
		deployer.undeploy(timeId);
		deployer.undeploy(logId);
		System.out.println("time after undeploy: " + deployer.status(timeId));
		System.out.println("log after undeploy:  " + deployer.status(logId));
	}

	private static AppDeploymentRequest createAppDeploymentRequest(String app, String stream) {
		MavenResource resource = new MavenResource.Builder()
				.artifactId(app)
				.groupId("org.springframework.cloud.stream.app")
				.version("1.0.0.BUILD-SNAPSHOT")
				.build();
		Map<String, String> properties = new HashMap<>();
		properties.put("server.port", "0");
		if (app.contains("-source-")) {
			properties.put("spring.cloud.stream.bindings.output.destination", stream);
		}
		else {
			properties.put("spring.cloud.stream.bindings.input.destination", stream);
			properties.put("spring.cloud.stream.bindings.input.group", "default");
		}
		AppDefinition definition = new AppDefinition(app, properties);
		Map<String, String> environmentProperties = Collections.singletonMap(AppDeployer.GROUP_PROPERTY_KEY, stream);
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, environmentProperties);
		return request;
	}
}
