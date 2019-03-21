/*
 * Copyright 2016 the original author or authors.
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

import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Creates a {@link LocalAppDeployer} and {@link LocalTaskLauncher}
 *
 * @author Mark Fisher
 */
@Configuration
@EnableConfigurationProperties(LocalDeployerProperties.class)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
public class LocalDeployerAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(AppDeployer.class)
	public AppDeployer appDeployer(LocalDeployerProperties properties) {
		return new LocalAppDeployer(properties);
	}

	@Bean
	@ConditionalOnMissingBean(TaskLauncher.class)
	public TaskLauncher taskLauncher(LocalDeployerProperties properties) {
		return new LocalTaskLauncher(properties);
	}
}
