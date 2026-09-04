package com.codinglair.taf.mcp.http;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Security and admission limits for the remote MCP endpoint. */
@Validated
@ConfigurationProperties("taf.mcp.http")
public final class TafMcpHttpProperties {
  private boolean enabled = true;
  @NotBlank private String issuerUri = "http://localhost:8081/realms/taf";
  @NotBlank private String audience = "taf-mcp";
  @NotBlank private String projectClaim = "taf_project";
  @NotBlank private String environmentClaim = "taf_environment";
  @NotBlank private String agentClaim = "taf_agent_id";

  @Min(1024)
  @Max(16_777_216)
  private int maxRequestBytes = 1_048_576;

  @Min(1)
  @Max(10_000)
  private int maxConcurrentRequests = 64;

  @Min(1)
  @Max(10_000)
  private int maxSessions = 256;

  @Min(1)
  @Max(100_000)
  private int requestsPerMinute = 120;

  private Duration requestTimeout = Duration.ofSeconds(30);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getIssuerUri() {
    return issuerUri;
  }

  public void setIssuerUri(String issuerUri) {
    this.issuerUri = issuerUri;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }

  public String getProjectClaim() {
    return projectClaim;
  }

  public void setProjectClaim(String projectClaim) {
    this.projectClaim = projectClaim;
  }

  public String getEnvironmentClaim() {
    return environmentClaim;
  }

  public void setEnvironmentClaim(String environmentClaim) {
    this.environmentClaim = environmentClaim;
  }

  public String getAgentClaim() {
    return agentClaim;
  }

  public void setAgentClaim(String agentClaim) {
    this.agentClaim = agentClaim;
  }

  public int getMaxRequestBytes() {
    return maxRequestBytes;
  }

  public void setMaxRequestBytes(int value) {
    maxRequestBytes = value;
  }

  public int getMaxConcurrentRequests() {
    return maxConcurrentRequests;
  }

  public void setMaxConcurrentRequests(int value) {
    maxConcurrentRequests = value;
  }

  public int getMaxSessions() {
    return maxSessions;
  }

  public void setMaxSessions(int value) {
    maxSessions = value;
  }

  public int getRequestsPerMinute() {
    return requestsPerMinute;
  }

  public void setRequestsPerMinute(int value) {
    requestsPerMinute = value;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(Duration value) {
    requestTimeout = value;
  }
}
