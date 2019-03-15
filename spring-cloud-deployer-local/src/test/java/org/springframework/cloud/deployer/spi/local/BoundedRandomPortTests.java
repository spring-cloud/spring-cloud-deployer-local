/*
 * Copyright 2018 the original author or authors.
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

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertThat;

/**
 * @author Christian Tzolov
 */
public class BoundedRandomPortTests {

	private AbstractLocalDeployerSupport localDeployerSupport;

	@Before
	public void setUp() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		properties.getPortBound().setLower(30001);
		properties.getPortBound().setUpper(30213);
		localDeployerSupport = new AbstractLocalDeployerSupport(properties) {};
	}

	@Test
	public void portTests() {
		//No exception should be thrown
		for (int i = 0; i < 30; i++) {
			System.out.println(i);
			int port = localDeployerSupport.getRandomPort();
			assertThat(port, Matchers.greaterThanOrEqualTo(30001));
			assertThat(port, Matchers.lessThanOrEqualTo(30213 + 5));
		}
	}
}
