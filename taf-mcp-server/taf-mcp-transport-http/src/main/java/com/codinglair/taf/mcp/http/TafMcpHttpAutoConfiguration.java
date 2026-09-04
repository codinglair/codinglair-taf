package com.codinglair.taf.mcp.http;

import com.codinglair.taf.mcp.prompts.McpPromptReportService;
import com.codinglair.taf.mcp.resources.McpResourceService;
import com.codinglair.taf.mcp.tools.McpWorkflowTools;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.time.Clock;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/** Conditional OIDC, admission-control, and shared-service HTTP composition. */
@AutoConfiguration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 100)
@EnableConfigurationProperties(TafMcpHttpProperties.class)
@ConditionalOnProperty(
    prefix = "taf.mcp.http",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TafMcpHttpAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  Clock tafMcpHttpClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  HttpCallerContext httpCallerContext(TafMcpHttpProperties properties) {
    return new HttpCallerContext(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
      @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
      McpServerStreamableHttpProperties serverProperties,
      HttpCallerContext caller) {
    return WebMvcStreamableServerTransportProvider.builder()
        .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
        .mcpEndpoint(serverProperties.getMcpEndpoint())
        .keepAliveInterval(serverProperties.getKeepAliveInterval())
        .disallowDelete(serverProperties.isDisallowDelete())
        .contextExtractor(_ -> caller.captureTransportContext())
        .build();
  }

  @Bean
  @ConditionalOnBean(McpWorkflowTools.class)
  @ConditionalOnMissingBean
  HttpWorkflowTools httpWorkflowTools(
      McpWorkflowTools workflows, HttpCallerContext caller, TafMcpHttpProperties properties) {
    return new HttpWorkflowTools(workflows, caller, properties);
  }

  @Bean
  @ConditionalOnBean(McpResourceService.class)
  @ConditionalOnMissingBean
  HttpResources httpResources(McpResourceService resources, HttpCallerContext caller) {
    return new HttpResources(resources, caller);
  }

  @Bean
  @ConditionalOnBean(McpPromptReportService.class)
  @ConditionalOnMissingBean
  HttpPromptReports httpPromptReports(McpPromptReportService prompts, HttpCallerContext caller) {
    return new HttpPromptReports(prompts, caller);
  }

  @Bean
  @ConditionalOnMissingBean
  org.springframework.security.oauth2.jwt.JwtDecoder tafMcpJwtDecoder(
      TafMcpHttpProperties properties) {
    NimbusJwtDecoder decoder =
        (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(properties.getIssuerUri());
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<Jwt>(
            JwtValidators.createDefaultWithIssuer(properties.getIssuerUri()),
            new AudienceValidator(properties.getAudience())));
    return decoder;
  }

  @Bean
  @ConditionalOnMissingBean(name = "tafMcpHttpSecurityFilterChain")
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  SecurityFilterChain tafMcpHttpSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.ignoringRequestMatchers("/mcp"))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness")
                    .permitAll()
                    .requestMatchers("/mcp")
                    .authenticated()
                    .requestMatchers("/actuator/**")
                    .denyAll()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
        .build();
  }

  @Bean
  @ConditionalOnMissingBean
  HttpAdmissionFilter httpAdmissionFilter(TafMcpHttpProperties properties, Clock clock) {
    return new HttpAdmissionFilter(properties, clock);
  }
}
