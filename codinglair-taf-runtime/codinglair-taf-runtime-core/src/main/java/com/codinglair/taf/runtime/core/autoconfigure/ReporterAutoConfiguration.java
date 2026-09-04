/*
 * Copyright 2026 Codinglair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codinglair.taf.runtime.core.autoconfigure;

import com.codinglair.taf.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.core.reporting.impl.NoOpReporter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for reporter implementations.
 *
 * <p>This core auto-configuration provides the vendor-neutral no-op fallback.
 *
 * <p>The framework remains provider-neutral; Allure is an optional implementation behind the
 * reporting SPI. Consumers can provide their own reporter implementations.
 *
 * @author Codinglair
 * @since 1.0
 */
@AutoConfiguration
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(TafRuntimeAutoConfiguration.class)
public class ReporterAutoConfiguration {

  /**
   * Creates a NoOpReporter as the default fallback when no TestReporter is defined.
   *
   * @return a NoOpReporter instance
   */
  @Bean
  @ConditionalOnMissingBean(TestReporter.class)
  public TestReporter noOpReporter() {
    return new NoOpReporter();
  }
}
