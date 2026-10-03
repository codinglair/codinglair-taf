package __BASE_PACKAGE__.configuration;

import com.codinglair.taf.mobile.MobileExecutionMode;
import com.codinglair.taf.mobile.appium.configuration.AppleProperties;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import java.io.IOException;
import java.util.ArrayList;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EnableConfigurationProperties(InteractionProperties.class)
public class MobileConfiguration {
  @Bean
  ConsumerPreflightContributor interactionPreflight(InteractionProperties interaction, AppleProperties apple) {
    return () -> {
      var diagnostics = new ArrayList<PreflightDiagnostic>();
      var settings = apple.settings("primary");
      var mode = settings.getExecutionMode();
      if ("UNRESOLVED".equals(settings.getDeviceId())) diagnostics.add(reference("device-id"));
      if ("UNRESOLVED".equals(settings.getBundleId())) diagnostics.add(reference("bundle-id"));
      if (settings.getAppReference() != null && settings.getAppReference().value().contains("/UNRESOLVED/"))
        diagnostics.add(reference("app-reference.value"));
      if (interaction.getExpectedText().isBlank()) diagnostics.add(missing("expected-text"));
      if (mode == MobileExecutionMode.NATIVE && interaction.getAccessibilityId().isBlank())
        diagnostics.add(missing("accessibility-id"));
      if (mode != MobileExecutionMode.NATIVE && interaction.getCssSelector().isBlank())
        diagnostics.add(missing("css-selector"));
      if (mode == MobileExecutionMode.HYBRID && interaction.getWebview().isBlank())
        diagnostics.add(missing("webview"));
      if (mode == MobileExecutionMode.SAFARI && interaction.getUrl().isBlank())
        diagnostics.add(missing("url"));
      return diagnostics;
    };
  }

  private static PreflightDiagnostic missing(String field) {
    return new PreflightDiagnostic("example.interaction." + field,
        "Consumer interaction prerequisite is missing", "Configure example.interaction." + field);
  }

  private static PreflightDiagnostic reference(String field) {
    return new PreflightDiagnostic("example.reference." + field,
        "Consumer target or application reference is unresolved",
        "Configure taf.mobile.apple.controllers.primary." + field);
  }

  public static final class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
      try {
        String resource = context.getEnvironment().getProperty("example.configuration", "capabilities/mobile.yml");
        if (!resource.matches("capabilities/mobile(?:-[a-z]+)?\\.yml"))
          throw new IllegalArgumentException("Select a packaged consumer mobile configuration");
        for (var source : new YamlPropertySourceLoader().load("mobile", new ClassPathResource(resource)))
          context.getEnvironment().getPropertySources().addLast(source);
      } catch (IOException failure) {
        throw new IllegalStateException("Cannot load consumer mobile configuration", failure);
      }
    }
  }
}
