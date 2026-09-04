package com.codinglair.taf.runtime.environment.spring;

import com.codinglair.taf.core.Capability;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import com.codinglair.taf.runtime.environment.EnvironmentStatus;
import com.codinglair.taf.runtime.environment.PreflightCheckResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Environment-owned configuration, selection, secret-reference, and readiness validation. */
final class EnvironmentConsumerPreflightContributor
    implements com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor {
  private final ConsumerConfigurationProperties configuration;
  private final SecretReferenceAvailability secrets;
  private final List<ConsumerPreflightContributor> contributors;

  EnvironmentConsumerPreflightContributor(
      ConsumerConfigurationProperties configuration,
      SecretReferenceAvailability secrets,
      List<ConsumerPreflightContributor> contributors) {
    this.configuration = configuration;
    this.secrets = secrets;
    this.contributors = List.copyOf(contributors);
  }

  @Override
  public List<PreflightDiagnostic> inspect() {
    List<PreflightDiagnostic> failures = new ArrayList<>();
    configuration
        .getCapabilities()
        .forEach(
            (name, instance) -> {
              if (!instance.isEnabled()) return;
              instance
                  .getRequiredValues()
                  .forEach(
                      (field, value) -> {
                        if (unresolved(value))
                          failures.add(
                              failure(
                                  name,
                                  instance,
                                  field,
                                  "required value is unresolved",
                                  "Set the required value for the active environment profile"));
                      });
              instance
                  .getSecretReferences()
                  .forEach(
                      (field, reference) -> {
                        if (unresolved(reference) || !secrets.isAvailable(reference)) {
                          failures.add(
                              failure(
                                  name,
                                  instance,
                                  field,
                                  "secret reference is unavailable",
                                  "Configure the referenced secret in the authorized execution environment"));
                        }
                      });
              instance.getRequires().stream()
                  .filter(required -> !enabled(required))
                  .forEach(
                      required ->
                          failures.add(
                              failure(
                                  name,
                                  instance,
                                  "requires",
                                  "required capability selection is absent",
                                  "Enable the required capability instance '" + required + "'")));
              instance.getIncompatibleWith().stream()
                  .filter(this::enabled)
                  .forEach(
                      incompatible ->
                          failures.add(
                              failure(
                                  name,
                                  instance,
                                  "incompatible-with",
                                  "incompatible capabilities are selected",
                                  "Disable either '" + name + "' or '" + incompatible + "'")));
            });
    for (int index = 0; index < contributors.size(); index++) {
      ConsumerPreflightContributor contributor = contributors.get(index);
      for (var entry : configuration.getCapabilities().entrySet()) {
        var instance = entry.getValue();
        if (!instance.isEnabled()
            || !java.util.Objects.equals(instance.getType(), contributor.capabilityType()))
          continue;
        try {
          List.copyOf(contributor.check(entry.getKey(), instance, configuration)).stream()
              .filter(EnvironmentConsumerPreflightContributor::blocksExecution)
              .map(EnvironmentConsumerPreflightContributor::diagnostic)
              .forEach(failures::add);
        } catch (RuntimeException failure) {
          failures.add(
              new PreflightDiagnostic(
                  "dependency." + entry.getKey() + "." + index,
                  "Dependency readiness check failed",
                  "Inspect the dependency configuration and sanitized provider diagnostics"));
        }
      }
    }
    return List.copyOf(failures);
  }

  private boolean enabled(String name) {
    var candidate = configuration.getCapabilities().get(name);
    return candidate != null && candidate.isEnabled();
  }

  private static boolean unresolved(String value) {
    return value == null
        || value.isBlank()
        || ConsumerConfigurationProperties.UNRESOLVED_VALUE.equals(value.trim());
  }

  private static boolean blocksExecution(PreflightCheckResult result) {
    return result.status() == EnvironmentStatus.UNKNOWN
        || result.status() == EnvironmentStatus.UNAVAILABLE
        || result.status() == EnvironmentStatus.MISCONFIGURED;
  }

  private PreflightDiagnostic failure(
      String name,
      ConsumerConfigurationProperties.CapabilityInstance instance,
      String field,
      String problem,
      String action) {
    String environment =
        configuration.getEnvironment() == null || configuration.getEnvironment().isBlank()
            ? "default"
            : configuration.getEnvironment().trim();
    Capability capability =
        new Capability(
            name,
            instance.getType() == null ? "custom" : instance.getType(),
            Capability.CapabilityType.CONTROLLER);
    return new PreflightDiagnostic(
        "configuration." + name + "." + field,
        "Capability '"
            + name
            + "' field '"
            + field
            + "' in environment '"
            + environment
            + "' "
            + problem,
        action,
        Map.of(
            "environment",
            environment,
            "field",
            field,
            "capability",
            capability.name(),
            "capabilityType",
            capability.type().name()));
  }

  private static PreflightDiagnostic diagnostic(PreflightCheckResult result) {
    return new PreflightDiagnostic(
        result.checkId(), result.message(), result.correctiveAction(), result.diagnostics());
  }
}
