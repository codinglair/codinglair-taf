package com.codinglair.taf.runtime.secret.spring;

import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import com.codinglair.taf.runtime.secret.SecretProvider;
import com.codinglair.taf.runtime.secret.SecretReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Validates only secret-reference metadata and selected-provider readiness. */
final class SecretPreflightContributor implements ConsumerPreflightContributor {
  private final SecretProperties properties;
  private final Map<String, SecretProvider> providers;

  SecretPreflightContributor(SecretProperties properties, List<SecretProvider> providers) {
    this.properties = properties;
    Map<String, SecretProvider> indexed = new HashMap<>();
    for (SecretProvider provider : providers) {
      if (indexed.putIfAbsent(provider.id(), provider) != null) {
        throw new IllegalStateException("Duplicate secret provider registration: " + provider.id());
      }
    }
    this.providers = Map.copyOf(indexed);
  }

  @Override
  public List<PreflightDiagnostic> inspect() {
    List<PreflightDiagnostic> diagnostics = new ArrayList<>();
    Map<String, SecretReference> selected = new HashMap<>();
    for (String configured : properties.getReferences()) {
      if (configured == null || configured.isBlank() || configured.contains("REPLACE_ME")) {
        diagnostics.add(
            failure(
                "reference",
                "A configured secret reference is unresolved",
                "Replace the placeholder with a canonical secret or credential reference"));
        continue;
      }
      try {
        SecretReference reference = SecretReference.parse(configured);
        selected.putIfAbsent(reference.provider(), reference);
      } catch (IllegalArgumentException invalid) {
        diagnostics.add(
            failure(
                "reference",
                invalid.getMessage(),
                "Use the documented version-one secret-reference grammar"));
      }
    }
    selected.forEach(
        (providerId, reference) -> {
          SecretProvider provider = providers.get(providerId);
          if (provider == null) {
            diagnostics.add(
                failure(
                    providerId,
                    "A selected secret provider is unavailable",
                    "Add exactly one authorized provider bean for the selected reference type"));
            return;
          }
          try {
            provider.verifyReady();
          } catch (RuntimeException unavailable) {
            diagnostics.add(
                failure(
                    providerId,
                    "A selected secret provider is not ready",
                    "Configure its authorized bootstrap source before execution"));
          }
        });
    return List.copyOf(diagnostics);
  }

  private static PreflightDiagnostic failure(String id, String message, String action) {
    return new PreflightDiagnostic(
        "secrets." + id, message, action, Map.of("capability", "secrets"));
  }
}
