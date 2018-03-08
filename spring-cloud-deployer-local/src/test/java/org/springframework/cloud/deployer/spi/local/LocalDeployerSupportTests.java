/*
 * Copyright 2017-2018 the original author or authors.
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

import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests for the AbstractLocalDeployerSupport
 *
 * @author Thomas Risberg
 */
public class LocalDeployerSupportTests {

	private LocalDeployerProperties localDeployerProperties;
	private AbstractLocalDeployerSupport localDeployerSupport;

	@Before
	public void setUp() {
		localDeployerProperties = new LocalDeployerProperties();
		localDeployerSupport = new AbstractLocalDeployerSupport(this.localDeployerProperties) {};
	}

	@Test
	public void testAppPropsAsSAJ() throws MalformedURLException {
		AppDeploymentRequest appDeploymentRequest = createAppDeploymentRequest();

		HashMap<String, String> envVarsToUse = new HashMap<>(appDeploymentRequest.getDefinition().getProperties());
		Map<String, String> environmentVariables = localDeployerSupport.formatApplicationProperties(appDeploymentRequest,
				envVarsToUse);

		assertThat(environmentVariables.size(), is(1));
		assertThat(environmentVariables.keySet(), hasItem(AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON));
		assertThat(environmentVariables.get(AbstractLocalDeployerSupport.SPRING_APPLICATION_JSON), is("{\"test.foo\":\"foo\",\"test.bar\":\"bar\"}"));
	}

	protected AppDeploymentRequest createAppDeploymentRequest() throws MalformedURLException {
		return createAppDeploymentRequest(new HashMap<>());
	}

	protected AppDeploymentRequest createAppDeploymentRequest(Map<String, String> depProps) throws MalformedURLException {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("test.foo", "foo");
		appProperties.put("test.bar", "bar");
		AppDefinition definition = new AppDefinition("randomApp", appProperties);
		return new AppDeploymentRequest(definition, testResource(), depProps);
	}


	protected Resource testResource() throws MalformedURLException {
		return new ClassPathResource("testResource.txt");
	}

}
