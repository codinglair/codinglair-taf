package com.codinglair.taf.mobile.appium.service;

import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.runtime.core.security.ResourceAccess;
import com.codinglair.taf.runtime.core.security.ResourceAuthorizer;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.openqa.selenium.json.Json;

/** Execution services, never bindable configuration or a serialized job credential container. */
public final class AppleTransportSecurity {
  private final ResourceAuthorizer authorizer;
  private final SecretManager secrets;
  private final String environment;

  public AppleTransportSecurity(
      ResourceAuthorizer authorizer, SecretManager secrets, String environment) {
    this.authorizer = Objects.requireNonNull(authorizer);
    this.secrets = secrets;
    this.environment = Objects.requireNonNull(environment);
  }

  /** Call only with operator-trusted configuration, never with unvalidated job overrides. */
  public static AppleTransportSecurity trusted(
      AppleControllerSettings settings, SecretManager secrets) {
    var grants = new HashSet<ResourceAccess>();
    if (settings.getServerUrl() != null)
      grants.add(
          new ResourceAccess(
              ResourceAccess.Kind.ENDPOINT, settings.getServerUrl().toASCIIString(), "*"));
    if (settings.getWda().getBaseUrl() != null)
      grants.add(
          new ResourceAccess(
              ResourceAccess.Kind.ENDPOINT, settings.getWda().getBaseUrl().toASCIIString(), "*"));
    grants.add(new ResourceAccess(ResourceAccess.Kind.TARGET, target(settings), "*"));
    if (!settings.getProviderSelection().isEmpty())
      grants.add(new ResourceAccess(ResourceAccess.Kind.TARGET, selection(settings), "*"));
    if (settings.getBundleId() != null)
      grants.add(new ResourceAccess(ResourceAccess.Kind.APPLICATION, settings.getBundleId(), "*"));
    if (settings.getAppReference() != null && settings.getAppReference().value() != null)
      grants.add(
          new ResourceAccess(
              ResourceAccess.Kind.APPLICATION, settings.getAppReference().value(), "*"));
    return new AppleTransportSecurity(ResourceAuthorizer.trusted(grants), secrets, "standalone");
  }

  public static String target(AppleControllerSettings settings) {
    if (settings.getAllocationResource() != null)
      return "allocation:" + settings.getAllocationResource();
    if (settings.getDeviceId() != null) return settings.getDeviceId();
    if (!settings.getProviderSelection().isEmpty()) return selection(settings);
    return "device-name:" + settings.getDeviceName();
  }

  private static String selection(AppleControllerSettings settings) {
    try {
      return "provider-selection:"
          + HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          new Json()
                              .toJson(canonical(settings.getProviderSelection()))
                              .getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException _) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  private static Object canonical(Object value) {
    return switch (value) {
      case Map<?, ?> map -> {
        var result = new TreeMap<String, Object>();
        map.forEach((key, item) -> result.put(key.toString(), canonical(item)));
        yield result;
      }
      case List<?> list -> list.stream().map(AppleTransportSecurity::canonical).toList();
      case null, default -> value;
    };
  }

  public void requireSettings(AppleControllerSettings settings) {
    require(ResourceAccess.Kind.ENDPOINT, settings.getServerUrl().toASCIIString(), "connect");
    require(ResourceAccess.Kind.TARGET, target(settings), "select");
    if (!settings.getProviderSelection().isEmpty())
      require(ResourceAccess.Kind.TARGET, selection(settings), "select");
    if (settings.getWda().getBaseUrl() != null)
      require(
          ResourceAccess.Kind.ENDPOINT, settings.getWda().getBaseUrl().toASCIIString(), "connect");
    if (settings.getAppReference() != null)
      require(ResourceAccess.Kind.APPLICATION, settings.getAppReference().value(), "initialize");
    if (settings.getBundleId() != null)
      require(ResourceAccess.Kind.APPLICATION, settings.getBundleId(), "initialize");
  }

  public void require(ResourceAccess.Kind kind, String resource, String action) {
    authorizer.require(new ResourceAccess(kind, resource, action));
  }

  /** Called only after resource checks succeed. Holders are closed inside each HTTP exchange. */
  public String resolve(String reference, String sessionId, AppleControllerSettings settings) {
    requireSettings(settings);
    if (secrets == null) throw new IllegalStateException("Apple secret manager is unavailable");
    try (var secret =
        secrets.resolve(
            reference, new SecretRequestContext("apple-transport", sessionId, environment, true))) {
      return secret.useAsString();
    } catch (RuntimeException _) {
      throw new IllegalStateException("Apple credential resolution failed safely");
    }
  }

  @Override
  public String toString() {
    return "AppleTransportSecurity[execution services]";
  }
}
