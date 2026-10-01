package com.codinglair.taf.mobile.appium.configuration;

import java.util.LinkedHashMap;
import java.util.Map;

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
    if (getMechanism() == Mechanism.NONE && !secretReferences.isEmpty())
      throw new IllegalArgumentException(
          "Apple authentication references require an explicit mechanism");
    if (getMechanism() != Mechanism.NONE && secretReferences.isEmpty())
      throw new IllegalArgumentException(
          "Apple authentication mechanism requires secret references");
    if (getMechanism() == Mechanism.BASIC
        && !(secretReferences.containsKey("username") && secretReferences.containsKey("password")))
      throw new IllegalArgumentException(
          "Apple BASIC authentication requires username and password references");
  }
}
