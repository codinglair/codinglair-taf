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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for TAF Runtime.
 *
 * <p>These properties control optional capabilities and are used by the auto-configuration classes
 * to conditionally register beans.
 *
 * @author Codinglair
 * @since 1.0
 */
@ConfigurationProperties(prefix = "taf")
public class TafRuntimeProperties {

  /** Whether the web controller (Playwright) is enabled. Default: true */
  private boolean webControllerEnabled = true;

  /** Whether the Allure reporter is enabled. Default: true */
  private boolean reporterAllureEnabled = true;

  /** Whether structured logging is enabled. Default: true */
  private boolean loggingStructured = true;

  /** Maximum number of cleanup listeners to invoke during session completion. Default: 100 */
  private int maxCleanupListeners = 100;

  /** Timeout for controller operations in milliseconds. Default: 30000 (30 seconds) */
  private int controllerTimeoutMs = 30000;

  /** Whether to enable strict mode (fail on missing controllers). Default: false */
  private boolean strictMode = false;

  public boolean isWebControllerEnabled() {
    return webControllerEnabled;
  }

  public void setWebControllerEnabled(boolean webControllerEnabled) {
    this.webControllerEnabled = webControllerEnabled;
  }

  public boolean isReporterAllureEnabled() {
    return reporterAllureEnabled;
  }

  public void setReporterAllureEnabled(boolean reporterAllureEnabled) {
    this.reporterAllureEnabled = reporterAllureEnabled;
  }

  public boolean isLoggingStructured() {
    return loggingStructured;
  }

  public void setLoggingStructured(boolean loggingStructured) {
    this.loggingStructured = loggingStructured;
  }

  public int getMaxCleanupListeners() {
    return maxCleanupListeners;
  }

  public void setMaxCleanupListeners(int maxCleanupListeners) {
    this.maxCleanupListeners = maxCleanupListeners;
  }

  public int getControllerTimeoutMs() {
    return controllerTimeoutMs;
  }

  public void setControllerTimeoutMs(int controllerTimeoutMs) {
    this.controllerTimeoutMs = controllerTimeoutMs;
  }

  public boolean isStrictMode() {
    return strictMode;
  }

  public void setStrictMode(boolean strictMode) {
    this.strictMode = strictMode;
  }
}
