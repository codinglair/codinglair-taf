package com.codinglair.taf.mobile.appium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.mobile.appium.configuration.AppleAutoConfiguration;
import com.codinglair.taf.mobile.appium.configuration.AppleProperties;
import com.codinglair.taf.mobile.appium.service.AppleController;
import com.codinglair.taf.mobile.appium.service.AppleControllerFactory;
import com.codinglair.taf.mobile.appium.service.AppleTransportSecurity;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflight;
import com.codinglair.taf.runtime.core.security.ResourceAuthorizer;
import com.codinglair.taf.runtime.secret.ResolvedSecret;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;
import io.appium.java_client.ios.IOSDriver;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

@DisplayName("Passive Apple Spring Boot composition")
class AppleAutoConfigurationTest {
  @Test
  @DisplayName("honors supplied resource denial for Apple and Android before network or secrets")
  void explicitDenial() {
    runner
        .withPropertyValues(
            "taf.mobile.apple.enabled=true",
            "taf.mobile.android.enabled=true",
            "taf.mobile.apple.platform=ios",
            "taf.mobile.apple.device-kind=simulator",
            "taf.mobile.apple.server-url=http://127.0.0.1:1",
            "taf.mobile.apple.device-name=fixture",
            "taf.mobile.apple.bundle-id=com.example.fixture",
            "taf.mobile.apple.authentication.mechanism=header",
            "taf.mobile.apple.authentication.secret-references.Authorization=secret://env/APPLE_KEY",
            "taf.mobile.android.server-url=http://127.0.0.1:1",
            "taf.mobile.android.device-name=fixture",
            "taf.mobile.android.app-package=com.example.fixture")
        .withBean(ResourceAuthorizer.class, () -> _ -> false)
        .withBean(
            SecretManager.class,
            () ->
                new SecretManager() {
                  public ResolvedSecret resolve(String reference, SecretRequestContext context) {
                    throw new AssertionError("Denied resource must precede secrets");
                  }

                  public void verifyReady(String reference) {
                    throw new AssertionError("Startup must stay passive");
                  }
                })
        .run(
            c -> {
              assertThat(c).hasNotFailed();
              var apple = c.getBean(AppleControllerFactory.class).create("default");
              assertThrows(RuntimeException.class, () -> apple.initialize(TestContexts.context()));
              apple.close();
              var android = c.getBean(AndroidControllerFactory.class).create("default");
              assertThrows(
                  RuntimeException.class, () -> android.initialize(TestContexts.context()));
              android.close();
            });
  }

  @Test
  @DisplayName("uses a caller supplied transport service without resolving it during startup")
  void securityOverride() {
    var security = new AppleTransportSecurity(_ -> false, null, "test");
    runner
        .withPropertyValues("taf.mobile.apple.enabled=true")
        .withBean(AppleTransportSecurity.class, () -> security)
        .run(
            c -> {
              assertThat(c).hasNotFailed();
              assertThat(c).hasSingleBean(AppleControllerFactory.class);
            });
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  AppleAutoConfiguration.class,
                  AndroidAutoConfiguration.class,
                  TafRuntimeAutoConfiguration.class));

  @Test
  @DisplayName("creates no Apple factory or driver when unconfigured or disabled")
  void disabled() {
    runner.run(
        c ->
            assertThat(c)
                .doesNotHaveBean(AppleControllerFactory.class)
                .doesNotHaveBean(IOSDriver.class));
    runner
        .withPropertyValues("taf.mobile.apple.enabled=false")
        .run(c -> assertThat(c).doesNotHaveBean(AppleControllerFactory.class));
  }

  @Test
  @DisplayName("backs off when the optional IOSDriver class is absent")
  void missingClass() {
    runner
        .withClassLoader(new FilteredClassLoader(IOSDriver.class))
        .withPropertyValues("taf.mobile.apple.enabled=true")
        .run(c -> assertThat(c).doesNotHaveBean(AppleControllerFactory.class));
  }

  @Test
  @DisplayName("registers independent lazy Android and Apple names without connection")
  void lazy() {
    runner
        .withPropertyValues(
            "taf.mobile.apple.enabled=true",
            "taf.mobile.android.enabled=true",
            "taf.mobile.apple.platform=ios",
            "taf.mobile.apple.device-kind=simulator",
            "taf.mobile.apple.server-url=http://127.0.0.1:1/custom",
            "taf.mobile.apple.bundle-id=com.example.fixture",
            "taf.mobile.apple.device-name=base",
            "taf.mobile.apple.auto-accept-alerts=true",
            "taf.mobile.apple.controllers.phone.device-name=named",
            "taf.mobile.apple.controllers.phone.auto-accept-alerts=false")
        .run(
            c -> {
              assertThat(c)
                  .hasSingleBean(AppleControllerFactory.class)
                  .doesNotHaveBean(IOSDriver.class);
              var properties = c.getBean(AppleProperties.class);
              assertThat(properties.settings("phone").getAutoAcceptAlerts()).isFalse();
              assertThat(properties.settings("phone").getServerUrl().getPath())
                  .isEqualTo("/custom");
              try (var session = c.getBean(TestSessionFactory.class).create()) {
                assertThat(
                        session
                            .getControllerRegistry()
                            .hasController(AppleController.class, "phone"))
                    .isTrue();
                assertThat(
                        session
                            .getControllerRegistry()
                            .hasController(AppleController.class, "default"))
                    .isFalse();
                assertThat(
                        session
                            .getControllerRegistry()
                            .hasController(AndroidController.class, "default"))
                    .isTrue();
              }
            });
  }

  @Test
  @DisplayName("reports invalid enabled configuration passively with sanitized preflight")
  void preflight() {
    runner
        .withPropertyValues("taf.mobile.apple.enabled=true")
        .run(
            c -> {
              assertThat(c.getBean(ConsumerPreflight.class).inspect().diagnostics())
                  .anyMatch(d -> d.checkId().equals("mobile-apple.default"));
              try (var session = c.getBean(TestSessionFactory.class).create()) {
                assertThat(
                        session
                            .getControllerRegistry()
                            .hasController(AppleController.class, "default"))
                    .isTrue();
              }
            });
  }

  @Test
  @DisplayName("binds packaged references and nested WDA options through Spring")
  void nestedBinding() {
    runner
        .withPropertyValues(
            "taf.mobile.apple.enabled=true",
            "taf.mobile.apple.platform=ipados",
            "taf.mobile.apple.device-kind=physical",
            "taf.mobile.apple.device-id=authorized-fixture",
            "taf.mobile.apple.device-name=fixture",
            "taf.mobile.apple.server-url=http://127.0.0.1:1/custom",
            "taf.mobile.apple.application-mode=packaged",
            "taf.mobile.apple.app-reference.kind=server-path",
            "taf.mobile.apple.app-reference.value=/server/Fixture.ipa",
            "taf.mobile.apple.app-reference.build-kind=physical",
            "taf.mobile.apple.wda.local-port=8101",
            "taf.mobile.apple.prerequisites.endpoint=true",
            "taf.mobile.apple.prerequisites.xcode=false",
            "taf.mobile.apple.allocation-resource=provider-allocation",
            "taf.mobile.apple.controllers.tablet.wda.mjpeg-port=9101")
        .run(
            c -> {
              assertThat(c).hasNotFailed();
              var settings = c.getBean(AppleProperties.class).settings("tablet");
              assertThat(settings.getAppReference().value()).isEqualTo("/server/Fixture.ipa");
              assertThat(settings.getWda().getLocalPort()).isEqualTo(8101);
              assertThat(settings.getWda().getMjpegPort()).isEqualTo(9101);
              assertThat(settings.getAllocationResource()).isEqualTo("provider-allocation");
              assertThat(settings.getPrerequisites())
                  .containsEntry("endpoint", true)
                  .containsEntry("xcode", false);
            });
  }

  @Test
  @DisplayName("preserves nested provider JSON types from configuration property sources")
  void providerBinding() {
    runner
        .withPropertyValues(
            "taf.mobile.apple.enabled=true",
            "taf.mobile.apple.platform=ios",
            "taf.mobile.apple.device-kind=simulator",
            "taf.mobile.apple.device-name=fixture",
            "taf.mobile.apple.server-url=http://127.0.0.1:1/custom",
            "taf.mobile.apple.bundle-id=com.example.fixture")
        .withInitializer(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(
                        new MapPropertySource(
                            "typed-fixture",
                            Map.of(
                                "taf.mobile.apple.provider-options[cloud:options].build",
                                7,
                                "taf.mobile.apple.provider-options[cloud:options].enabled",
                                true))))
        .run(
            c -> {
              assertThat(c).hasNotFailed();
              assertThat(c.getBean(AppleProperties.class).settings("default").getProviderOptions())
                  .isEqualTo(Map.of("cloud:options", Map.of("build", 7, "enabled", true)));
            });
  }

  @Test
  @DisplayName("retains a consumer supplied controller factory")
  void overrideFactory() {
    AppleControllerFactory custom =
        name -> {
          throw new AssertionError("Factory should not run during startup");
        };
    runner
        .withPropertyValues("taf.mobile.apple.enabled=true")
        .withBean(AppleControllerFactory.class, () -> custom)
        .run(c -> assertThat(c.getBean(AppleControllerFactory.class)).isSameAs(custom));
  }
}
