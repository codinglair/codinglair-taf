package com.codinglair.taf.mobile.appium.configuration;

import com.codinglair.taf.runtime.secret.SecretReference;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Opaque references only. Resolution belongs to the separately authorized transport boundary. */
public class AppleAuthentication {
  public enum Mechanism {
    NONE,
    HEADER,
    BASIC,
    PROVIDER_CAPABILITY
  }

  private Mechanism mechanism;
  private Map<String, String> secretReferences = Map.of();

  public Mechanism getMechanism() {
    return mechanism == null ? Mechanism.NONE : mechanism;
  }

  public void setMechanism(Mechanism value) {
    mechanism = value;
  }

  public Map<String, String> getSecretReferences() {
    return secretReferences;
  }

  public void setSecretReferences(Map<String, String> value) {
    if (value == null
        || value.size() > 32
        || value.values().stream()
            .anyMatch(
                reference ->
                    reference == null
                        || reference.length() > 4096
                        || reference.contains("..")
                        || reference.contains("${")
                        || !reference.matches(
                            "(?:credential://[a-z0-9][a-z0-9._/-]{0,255}|secret://(?:env/[A-Z_][A-Z0-9_]{0,127}|jasypt/[A-Za-z0-9+/=_-]{16,4000}))")))
      throw new IllegalArgumentException("Apple authentication requires opaque secret references");
    secretReferences = Map.copyOf(value);
    secretReferences.values().forEach(SecretReference::parse);
  }

  static AppleAuthentication merge(AppleAuthentication base, AppleAuthentication overlay) {
    var result = new AppleAuthentication();
    result.mechanism = overlay.mechanism == null ? base.mechanism : overlay.mechanism;
    var references = new LinkedHashMap<>(base.secretReferences);
    references.putAll(overlay.secretReferences);
    result.setSecretReferences(references);
    return result;
  }

  void validate() {
    validateMechanismSelection();
    validateBasicReferences();
    validateHeaderReferences();
    validateProviderCapabilityReferences();
  }

  private void validateMechanismSelection() {
    if (getMechanism() == Mechanism.NONE && !secretReferences.isEmpty())
      throw new IllegalArgumentException(
          "Apple authentication references require an explicit mechanism");
    if (getMechanism() != Mechanism.NONE && secretReferences.isEmpty())
      throw new IllegalArgumentException(
          "Apple authentication mechanism requires secret references");
  }

  private void validateBasicReferences() {
    if (getMechanism() == Mechanism.BASIC
        && !secretReferences.keySet().equals(Set.of("username", "password")))
      throw new IllegalArgumentException(
          "Apple BASIC authentication requires username and password references");
  }

  private void validateHeaderReferences() {
    if (getMechanism() == Mechanism.HEADER
        && secretReferences.keySet().stream()
            .anyMatch(
                key ->
                    !key.matches("[A-Za-z][A-Za-z0-9-]{0,63}")
                        || Set.of(
                                "host",
                                "content-length",
                                "transfer-encoding",
                                "connection",
                                "upgrade")
                            .contains(key.toLowerCase(Locale.ROOT))))
      throw new IllegalArgumentException("Apple authentication header name is invalid");
  }

  private void validateProviderCapabilityReferences() {
    if (getMechanism() != Mechanism.PROVIDER_CAPABILITY) return;
    var paths = new HashSet<String>();
    secretReferences.keySet().forEach(path -> validateProviderCapabilityReference(path, paths));
  }

  private void validateProviderCapabilityReference(String path, Set<String> paths) {
    if (!path.matches(
        "/[A-Za-z][A-Za-z0-9_.-]*:[A-Za-z][A-Za-z0-9_.-]*(?:/[A-Za-z][A-Za-z0-9_.-]*)*"))
      throw new IllegalArgumentException(
          "Apple credential capability requires a provider JSON path");
    JsonOptions.validateCapabilities(providerCapability(path));
    if (paths.stream()
        .anyMatch(existing -> existing.startsWith(path + "/") || path.startsWith(existing + "/")))
      throw new IllegalArgumentException("Apple credential capability paths overlap");
    paths.add(path);
  }

  private Map<String, Object> providerCapability(String path) {
    String[] segments = path.substring(1).split("/");
    var candidate = new LinkedHashMap<String, Object>();
    Map<String, Object> current = candidate;
    for (int i = 0; i < segments.length - 1; i++) {
      var next = new LinkedHashMap<String, Object>();
      current.put(segments[i], next);
      current = next;
    }
    current.put(
        segments[segments.length - 1], Map.of("secretReference", secretReferences.get(path)));
    return candidate;
  }
}
