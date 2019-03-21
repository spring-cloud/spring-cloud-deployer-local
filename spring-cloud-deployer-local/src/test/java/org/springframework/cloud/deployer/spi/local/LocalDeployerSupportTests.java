/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.deployer.spi.local;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.config.RequestConfig;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.hamcrest.CoreMatchers.not;
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

	@Test
	public void testCalcServerPort() throws MalformedURLException {
		Map<String, String> applicationProperties = new HashMap<>();
		Map<String, String> deploymentPropertites = new HashMap<>();
		List<String> commandLineArgs = new ArrayList<>();

		// test adding to application properties
		applicationProperties.put("server.port", "9292");
		AppDefinition definition = new AppDefinition("randomApp", applicationProperties);

		AppDeploymentRequest appDeploymentRequest = new AppDeploymentRequest(definition, testResource(),
				deploymentPropertites, commandLineArgs);

		int portToUse = localDeployerSupport.calcServerPort(appDeploymentRequest, false, new HashMap<>());
		assertThat(portToUse, is(9292));

		// test adding to command line args, which has higher precedence than application properties
		commandLineArgs.add(LocalTaskLauncher.SERVER_PORT_KEY_COMMAND_LINE_ARG  + 9191);
		appDeploymentRequest = new AppDeploymentRequest(definition, testResource(),
				deploymentPropertites, commandLineArgs);

		portToUse = localDeployerSupport.calcServerPort(appDeploymentRequest, false, new HashMap<>());
		assertThat(portToUse, is(9191));

		// test using dynamic port assignment
		portToUse = localDeployerSupport.calcServerPort(appDeploymentRequest, true, new HashMap<>());
		assertThat(portToUse, not(9191));
		assertThat(portToUse, not(9292));
	}

	@Test
	public void testShutdownPropertyConfiguresRequestFactory() throws Exception {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		properties.setShutdownTimeout(1);
		AbstractLocalDeployerSupport abstractLocalDeployerSupport = new AbstractLocalDeployerSupport(properties) {};
		Object restTemplate = ReflectionTestUtils.getField(abstractLocalDeployerSupport, "restTemplate");
		Object requestFactory = ReflectionTestUtils.getField(restTemplate, "requestFactory");
		Object connectTimeout = ReflectionTestUtils.getField(requestFactory, "connectTimeout");
		Object readTimeout = ReflectionTestUtils.getField(requestFactory, "readTimeout");
		assertThat(connectTimeout, is(1000));
		assertThat(readTimeout, is(1000));
	}

	@Test
	public void testShutdownPropertyNotConfiguresRequestFactory() throws Exception {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		properties.setShutdownTimeout(-1);
		AbstractLocalDeployerSupport abstractLocalDeployerSupport = new AbstractLocalDeployerSupport(properties) {};
		Object restTemplate = ReflectionTestUtils.getField(abstractLocalDeployerSupport, "restTemplate");
		Object requestFactory = ReflectionTestUtils.getField(restTemplate, "requestFactory");
		Object connectTimeout =  ReflectionTestUtils.getField(requestFactory,"connectTimeout");
		Object readTimeout = ReflectionTestUtils.getField(requestFactory, "readTimeout");
		assertThat(connectTimeout, is(-1));
		assertThat(readTimeout, is(-1));
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
