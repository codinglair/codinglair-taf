package com.codinglair.taf.runtime.core.reporting.impl.allure;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed configuration for optional, post-run Allure single-file publication. */
@ConfigurationProperties("taf.reporting.allure.single-file")
public class AllureSingleFileProperties {
  private boolean enabled;
  private Path outputDirectory = Path.of("target", "taf-reports");
  private Path resultsDirectory = Path.of("target", "allure-results");
  private String reportName = "TAF Test Report";
  private String timestampPattern = "yyyyMMddHHmm";
  private String executable = "allure";
  private Duration timeout = Duration.ofMinutes(2);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Path getOutputDirectory() {
    return outputDirectory;
  }

  public void setOutputDirectory(Path outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  public Path getResultsDirectory() {
    return resultsDirectory;
  }

  public void setResultsDirectory(Path resultsDirectory) {
    this.resultsDirectory = resultsDirectory;
  }

  public String getReportName() {
    return reportName;
  }

  public void setReportName(String reportName) {
    this.reportName = reportName;
  }

  public String getTimestampPattern() {
    return timestampPattern;
  }

  public void setTimestampPattern(String timestampPattern) {
    this.timestampPattern = timestampPattern;
  }

  public String getExecutable() {
    return executable;
  }

  public void setExecutable(String executable) {
    this.executable = executable;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }
}
