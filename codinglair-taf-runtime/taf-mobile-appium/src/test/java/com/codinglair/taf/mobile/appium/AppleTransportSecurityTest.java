package com.codinglair.taf.mobile.appium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.mobile.MobileTopology;
import com.codinglair.taf.mobile.appium.configuration.AppleAuthentication.Mechanism;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.platform.ApplePlatformStrategy;
import com.codinglair.taf.mobile.appium.service.AppleResourceReservations;
import com.codinglair.taf.mobile.appium.service.AppleTransportSecurity;
import com.codinglair.taf.mobile.appium.service.DefaultAppleController;
import com.codinglair.taf.runtime.core.security.ResourceAccess;
import com.codinglair.taf.runtime.secret.ResolvedSecret;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.json.Json;

@DisplayName("Apple authorized transport, nested credentials and retained-output isolation")
class AppleTransportSecurityTest extends AppleProtocolFixture {
  static final String CANARY = "FAKE_APPLE_SECRET_130";
  final List<ResolvedSecret> holders = new CopyOnWriteArrayList<>();
  final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
  final AtomicInteger resolutions = new AtomicInteger();
  boolean redirect;
  boolean failResponse;
  SecretManager secrets =
      new SecretManager() {
        @Override
        public ResolvedSecret resolve(String reference, SecretRequestContext context) {
          assertThat(context.authorized()).isTrue();
          resolutions.incrementAndGet();
          var holder =
              ResolvedSecret.of((reference.endsWith("USER") ? "fake-user" : CANARY).toCharArray());
          holders.add(holder);
          return holder;
        }

        @Override
        public void verifyReady(String reference) {
          throw new AssertionError("Discovery must not resolve or probe");
        }
      };

  @BeforeEach
  void secureFixture() {
    server.removeContext("/");
    server.createContext("/", this::handleSecureRequest);
  }

  private void handleSecureRequest(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    String path = exchange.getRequestURI().getPath();
    requests.add(exchange.getRequestMethod() + " " + path + " " + body);
    recordAuthorizationHeader(exchange);
    if (redirect) {
      writeRedirect(exchange);
      return;
    }
    writeSecureResponse(exchange, secureResponse(path));
  }

  private void recordAuthorizationHeader(HttpExchange exchange) {
    String header = exchange.getRequestHeaders().getFirst("Authorization");
    authorizationHeaders.add(header == null ? "absent" : header);
  }

  private void writeRedirect(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().set("Location", "http://127.0.0.1:1/denied?token=" + CANARY);
    exchange.sendResponseHeaders(302, -1);
    exchange.close();
  }

  private Object secureResponse(String path) {
    if (failResponse)
      return Map.of("error", "unknown error", "message", CANARY, "stacktrace", CANARY);
    if (path.equals("/session"))
      return Map.of(
          "sessionId",
          "secure-fixture",
          "capabilities",
          Map.of(
              "platformName",
              "iOS",
              "cloud:options",
              Map.of("accessKey", CANARY),
              "note",
              CANARY,
              "artifact",
              "https://artifact.example/item?signature=" + CANARY));
    if (path.endsWith("/source")) return "<source>" + CANARY + "</source>";
    if (path.endsWith("/screenshot"))
      return Base64.getEncoder().encodeToString(CANARY.getBytes(StandardCharsets.UTF_8));
    return null;
  }

  private void writeSecureResponse(HttpExchange exchange, Object value) throws IOException {
    byte[] bytes =
        new Json()
            .toJson(Map.of("value", value == null ? Map.of() : value))
            .getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(failResponse ? 500 : 200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  void initializeSecure(AppleControllerSettings settings, AppleTransportSecurity security) {
    controller =
        new DefaultAppleController(
            "secure", settings, null, new AppleResourceReservations(), security);
    controller.initialize(TestContexts.context());
  }

  @ParameterizedTest
  @EnumSource(
      value = Mechanism.class,
      names = {"HEADER", "BASIC", "PROVIDER_CAPABILITY"})
  @DisplayName("resolves each supported mechanism only inside authorized HTTP exchanges")
  void mechanisms(Mechanism mechanism) {
    var settings = settings();
    settings.getAuthentication().setMechanism(mechanism);
    settings
        .getAuthentication()
        .setSecretReferences(
            switch (mechanism) {
              case HEADER -> Map.of("Authorization", "secret://env/APPLE_PASSWORD");
              case BASIC ->
                  Map.of(
                      "username",
                      "secret://env/APPLE_USER",
                      "password",
                      "secret://env/APPLE_PASSWORD");
              case PROVIDER_CAPABILITY ->
                  Map.of("/cloud:options/account/accessKey", "secret://env/APPLE_PASSWORD");
              default -> throw new AssertionError();
            });
    var security = AppleTransportSecurity.trusted(settings, secrets);
    assertThat(new ApplePlatformStrategy().options(settings).asMap().toString())
        .doesNotContain(CANARY);
    assertThat(resolutions.get()).isZero();
    initializeSecure(settings, security);
    assertThat(controller.nativeDriver().getCapabilities().toString()).doesNotContain(CANARY);
    assertThat(controller.nativeDriver().getPageSource()).doesNotContain(CANARY);
    assertThat(controller.nativeDriver().getScreenshotAs(OutputType.BASE64)).isEmpty();
    assertThat(controller.health().toString()).doesNotContain(CANARY);
    controller.close();
    assertThat(holders).allMatch(ResolvedSecret::isClosed);
    assertThat(settings.providerCapabilities().toString()).doesNotContain(CANARY);
    if (mechanism == Mechanism.PROVIDER_CAPABILITY) {
      assertThat(requests.getFirst()).contains("account", "accessKey", CANARY);
      assertThat(authorizationHeaders).containsOnly("absent");
    } else
      assertThat(authorizationHeaders)
          .allMatch(value -> value.contains(mechanism == Mechanism.BASIC ? "Basic " : CANARY));
    assertThat(requests.stream().filter(request -> request.startsWith("DELETE")).count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("nested typed references resolve without changing settings or discovery options")
  void nestedReferences() {
    var settings = settings();
    settings.setProviderOptions(
        Map.of(
            "cloud:options",
            Map.of(
                "account",
                Map.of("accessKey", Map.of("secretReference", "secret://env/APPLE_PASSWORD")),
                "attempts",
                3)));
    var original = settings.providerCapabilities();
    initializeSecure(settings, AppleTransportSecurity.trusted(settings, secrets));
    assertThat(requests.getFirst()).contains(CANARY).doesNotContain("secretReference");
    assertThat(settings.providerCapabilities()).isEqualTo(original);
    assertThat(controller.nativeDriver().getCapabilities().toString()).doesNotContain(CANARY);
    assertThat(holders).allMatch(ResolvedSecret::isClosed);
  }

  @ParameterizedTest
  @EnumSource(
      value = ResourceAccess.Kind.class,
      names = {"ENDPOINT", "TARGET", "APPLICATION"})
  @DisplayName("explicit resource denials fail before connections or secret retrieval")
  void denied(ResourceAccess.Kind denied) {
    var settings = settings();
    settings.getAuthentication().setMechanism(Mechanism.HEADER);
    settings
        .getAuthentication()
        .setSecretReferences(Map.of("Authorization", "secret://env/APPLE_PASSWORD"));
    initializeDenied(
        settings, new AppleTransportSecurity(access -> access.kind() != denied, secrets, "test"));
    assertThat(requests).isEmpty();
    assertThat(resolutions.get()).isZero();
  }

  void initializeDenied(AppleControllerSettings settings, AppleTransportSecurity security) {
    var failure = assertThrows(RuntimeException.class, () -> initializeSecure(settings, security));
    assertThat(failure.toString()).doesNotContain(CANARY);
  }

  @Test
  @DisplayName(
      "trusted configuration does not authorize endpoint, target or application job overrides")
  void overrides() {
    var trusted = settings();
    trusted.setDeviceId("trusted-device");
    var security = AppleTransportSecurity.trusted(trusted, secrets);
    for (String field : List.of("endpoint", "target", "application")) {
      var overlay = new AppleControllerSettings();
      switch (field) {
        case "endpoint" -> overlay.setServerUrl(URI.create("http://127.0.0.1:1/override"));
        case "target" -> overlay.setDeviceId("job-device");
        case "application" -> overlay.setBundleId("job.bundle");
        default -> throw new AssertionError();
      }
      assertThrows(
          SecurityException.class,
          () -> security.requireSettings(AppleControllerSettings.resolve(trusted, null, overlay)));
    }
    assertThat(resolutions.get()).isZero();
    assertThat(requests).isEmpty();
  }

  @Test
  @DisplayName("provider-side target selection is trusted without enumerating provider devices")
  void selection() {
    var settings = settings();
    settings.setTopology(MobileTopology.PROVIDER);
    settings.setProviderSelection(
        Map.of("cloud:selection", Map.of("model", "phone", "pool", "trusted")));
    initializeSecure(settings, AppleTransportSecurity.trusted(settings, secrets));
    assertThat(requests.getFirst()).contains("cloud:selection", "trusted");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "appium:udid",
        "/cloud:options/udid",
        "/cloud:options/noReset",
        "/cloud:options/endpoint"
      })
  @DisplayName("credential capability paths cannot bypass typed invariants or resource ownership")
  void reservedCredentialPaths(String path) {
    var settings = settings();
    settings.getAuthentication().setMechanism(Mechanism.PROVIDER_CAPABILITY);
    settings.getAuthentication().setSecretReferences(Map.of(path, "secret://env/APPLE_PASSWORD"));
    assertThrows(IllegalArgumentException.class, settings::validate);
    assertThat(requests).isEmpty();
  }

  @Test
  @DisplayName("redirect and remote failures expose neither credentials nor signed locations")
  void failures() {
    var settings = settings();
    settings.getAuthentication().setMechanism(Mechanism.HEADER);
    settings
        .getAuthentication()
        .setSecretReferences(Map.of("Authorization", "secret://env/APPLE_PASSWORD"));
    redirect = true;
    initializeDenied(settings, AppleTransportSecurity.trusted(settings, secrets));
    controller.close();
    controller = null;
    redirect = false;
    failResponse = true;
    initializeDenied(settings, AppleTransportSecurity.trusted(settings, secrets));
    assertThat(holders).allMatch(ResolvedSecret::isClosed);
  }

  @Test
  @DisplayName("native application escape hatches still enforce resource permission")
  void nativeApplicationDenied() {
    var settings = settings();
    initializeSecure(settings, AppleTransportSecurity.trusted(settings, secrets));
    int before = requests.size();
    assertThrows(
        RuntimeException.class,
        () -> controller.nativeDriver().installApp("/unauthorized/Other.app"));
    assertThat(requests).hasSize(before);
  }

  @Test
  @DisplayName("cancellation preserves interruption and performs no credential retrieval")
  void cancelled() {
    var settings = settings();
    Thread.currentThread().interrupt();
    try {
      initializeDenied(settings, AppleTransportSecurity.trusted(settings, secrets));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(requests).isEmpty();
      assertThat(resolutions.get()).isZero();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  @DisplayName(
      "credential resolution failure before connection releases the reservation and omits raw causes")
  void secretFailureReleasesReservation() throws Exception {
    var settings = settings();
    settings.setDeviceId("credential-failure-target");
    settings.getAuthentication().setMechanism(Mechanism.HEADER);
    settings
        .getAuthentication()
        .setSecretReferences(Map.of("Authorization", "secret://env/APPLE_PASSWORD"));
    var failingSecrets =
        new SecretManager() {
          public ResolvedSecret resolve(String reference, SecretRequestContext context) {
            throw new IllegalStateException(CANARY);
          }

          public void verifyReady(String reference) {
            throw new AssertionError();
          }
        };
    var reservations = new AppleResourceReservations();
    controller =
        new DefaultAppleController(
            "secret-failure",
            settings,
            null,
            reservations,
            AppleTransportSecurity.trusted(settings, failingSecrets));
    var failure =
        assertThrows(RuntimeException.class, () -> controller.initialize(TestContexts.context()));
    assertThat(failure)
        .hasCause(null)
        .hasMessageNotContaining(CANARY)
        .hasMessageNotContaining("uncertain");
    assertThat(requests).isEmpty();
    try (var lease = reservations.acquire(settings)) {
      assertThat(lease).isNotNull();
    }
  }

  @Test
  @DisplayName("Selenium debug diagnostics retain no resolved credentials or signed URL tokens")
  void logging() {
    var records = new CopyOnWriteArrayList<String>();
    Handler handler =
        new Handler() {
          public void publish(LogRecord record) {
            records.add(record.getMessage() + " " + Arrays.toString(record.getParameters()));
          }

          public void flush() {}

          public void close() {}
        };
    var logger = Logger.getLogger("org.openqa.selenium.remote.RemoteWebDriver");
    Level prior = logger.getLevel();
    logger.addHandler(handler);
    logger.setLevel(Level.ALL);
    handler.setLevel(Level.ALL);
    try {
      var settings = settings();
      settings.getAuthentication().setMechanism(Mechanism.HEADER);
      settings
          .getAuthentication()
          .setSecretReferences(Map.of("Authorization", "secret://env/APPLE_PASSWORD"));
      initializeSecure(settings, AppleTransportSecurity.trusted(settings, secrets));
      controller.close();
      assertThat(records).isNotEmpty().allMatch(record -> !record.contains(CANARY));
    } finally {
      logger.removeHandler(handler);
      logger.setLevel(prior);
    }
  }
}
