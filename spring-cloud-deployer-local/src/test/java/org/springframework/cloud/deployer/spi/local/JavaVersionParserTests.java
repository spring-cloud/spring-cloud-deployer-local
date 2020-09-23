/*
 * Copyright 2020 the original author or authors.
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaVersionParserTests {
	@Test
	void testDefault() {
		int version = new AbstractLocalDeployerSupport.JavaVersionParser().versionAsOrdinal();
		String versionAsString = System.getProperty("java.specification.version");
		switch (versionAsString) {
			case "1.8":
				assertThat(version).isEqualTo(8);
				break;
			default:
				assertThat(version).isEqualTo(Integer.valueOf(version));
				break;
		}
	}

	@Test
	void test11() {
		int version = new AbstractLocalDeployerSupport.JavaVersionParser("11").versionAsOrdinal();
		assertThat(version).isEqualTo(11);

	}
}
