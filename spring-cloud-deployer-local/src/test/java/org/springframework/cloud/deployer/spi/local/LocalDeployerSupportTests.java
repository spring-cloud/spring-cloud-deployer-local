/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.deployer.spi.local;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests for the AbstractLocalDeployerSupport
 *
 * @author Thomas Risberg
 */
public class LocalDeployerSupportTests {

	private Map<String, String> deploymentProperties;
	private LocalDeployerProperties localDeployerProperties;
	private AbstractLocalDeployerSupport localDeployerSupport;

	@Before
	public void setUp() {
		deploymentProperties = new HashMap<>();
		localDeployerProperties = new LocalDeployerProperties();
		localDeployerSupport = new AbstractLocalDeployerSupport(this.localDeployerProperties) {};
	}

	@Test
	public void testAppPropsAsCommandLineArgs() throws MalformedURLException {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("test.foo", "foo");
		appProperties.put("test.bar", "bar");
		AppDefinition definition = new AppDefinition("randomApp", appProperties);
		deploymentProperties.put(AbstractLocalDeployerSupport.USE_SPRING_APPLICATION_JSON_KEY, "false");
		AppDeploymentRequest appDeploymentRequest =
				new AppDeploymentRequest(definition, testResource(), deploymentProperties);

		HashMap<String, String> envVarsToUse = new HashMap<>();
		HashMap<String, String> appPropsToUse = new HashMap<>();
		localDeployerSupport.handleAppPropertiesPassing(appDeploymentRequest, appProperties, envVarsToUse, appPropsToUse);
		assertThat(appPropsToUse.size(), is(2));
		assertThat(envVarsToUse.size(), is(0));
		assertThat(appPropsToUse.get("test.foo"), is("foo"));
		assertThat(appPropsToUse.get("test.bar"), is("bar"));
	}

	@Test
	public void testAppPropsAsSAJ() throws MalformedURLException {
		AppDeploymentRequest appDeploymentRequest = createAppDeploymentRequest();

		HashMap<String, String> envVarsToUse = new HashMap<>();
		HashMap<String, String> appPropsToUse = new HashMap<>();
		localDeployerSupport.handleAppPropertiesPassing(appDeploymentRequest,
				appDeploymentRequest.getDefinition().getProperties(),
				envVarsToUse,
				appPropsToUse);

		assertThat(appPropsToUse.size(), is(0));
		assertThat(envVarsToUse.size(), is(1));
		assertThat(envVarsToUse.keySet(), hasItem(AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON));
		assertThat(envVarsToUse.get(AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON), is("{\"test.foo\":\"foo\",\"test.bar\":\"bar\"}"));
	}

	protected AppDeploymentRequest createAppDeploymentRequest() throws MalformedURLException {
		return createAppDeploymentRequest(new HashMap<>());
	}

	protected AppDeploymentRequest createAppDeploymentRequest(Map<String, String> depProps) throws MalformedURLException {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("test.foo", "foo");
		appProperties.put("test.bar", "bar");
		AppDefinition definition = new AppDefinition("randomApp", appProperties);
		deploymentProperties.put("spring.cloud.deployer.local.use-spring-application-json", "false");
		return new AppDeploymentRequest(definition, testResource(), depProps);
	}


	protected Resource testResource() throws MalformedURLException {
		return new ClassPathResource("testResource.txt");
	}

}
