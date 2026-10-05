package com.codinglair.taf.mcp.resources;

import com.codinglair.taf.core.Capability;
import com.codinglair.taf.mobile.ApplePlatformManifest;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable catalog derived from authoritative Runtime capability value objects. */
public final class RuntimeCapabilityCatalog {
  private final List<RuntimeCapabilityDescriptor> descriptors;

  public RuntimeCapabilityCatalog(
      Collection<Capability> supported,
      Map<String, String> installedRuntimeVersions,
      String baselineRuntimeVersion) {
    Objects.requireNonNull(supported, "supported");
    Objects.requireNonNull(installedRuntimeVersions, "installedRuntimeVersions");
    if (baselineRuntimeVersion == null || baselineRuntimeVersion.isBlank()) {
      throw new IllegalArgumentException("baselineRuntimeVersion is required");
    }
    var names = new HashSet<String>();
    descriptors =
        supported.stream()
            .peek(
                capability -> {
                  if (!names.add(capability.name())) {
                    throw new IllegalArgumentException(
                        "duplicate capability: " + capability.name());
                  }
                })
            .map(
                capability ->
                    descriptor(capability, installedRuntimeVersions, baselineRuntimeVersion))
            .sorted(Comparator.comparing(RuntimeCapabilityDescriptor::id))
            .toList();
    var unknown = new HashSet<>(installedRuntimeVersions.keySet());
    unknown.removeAll(names);
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException("installed capabilities are not supported: " + unknown);
    }
  }

  public ResourcePage<RuntimeCapabilityDescriptor> discover(ResourceQuery query) {
    return DeterministicPaginator.page(
        descriptors,
        query,
        descriptor ->
            descriptor.id()
                + " "
                + descriptor.capability().descriptor()
                + " "
                + descriptor.capability().type());
  }

  public List<RuntimeCapabilityDescriptor> descriptors() {
    return descriptors;
  }

  private static RuntimeCapabilityDescriptor descriptor(
      Capability capability, Map<String, String> installedVersions, String baselineVersion) {
    var installedVersion = installedVersions.get(capability.name());
    var details =
        switch (capability.name()) {
          case "aws.eventbridge" ->
              new String[][] {
                {"publish", "verify-route", "assert-not-routed"},
                {"route observation requires a configured SQS target"},
                {"taf.aws.connections.<profile>.eventbridge.<instance>"}
              };
          case "aws.sqs" ->
              new String[][] {
                {
                  "send",
                  "receive",
                  "await",
                  "assert-none",
                  "acknowledge",
                  "visibility",
                  "diagnostics"
                },
                {"all observation is bounded", "receipt handles are never returned"},
                {"taf.aws.connections.<profile>.sqs.<instance>"}
              };
          case ApplePlatformManifest.CAPABILITY_ID -> {
            var apple = ApplePlatformManifest.descriptor();
            yield new String[][] {
              {
                "discover",
                "validate",
                "execute-fixture",
                "cancel",
                "scaffold",
                "screenshot",
                "source",
                "syslog",
                "video"
              },
              apple.limitations().toArray(String[]::new),
              {
                "taf.mobile.apple.instances.<name>",
                "external Appium/XCUITest endpoint and authorized target"
              }
            };
          }
          default -> new String[][] {{}, {}, {}};
        };
    return new RuntimeCapabilityDescriptor(
        capability,
        installedVersion == null ? baselineVersion : installedVersion,
        installedVersion == null
            ? RuntimeCapabilityDescriptor.InstallationStatus.ABSENT
            : RuntimeCapabilityDescriptor.InstallationStatus.INSTALLED,
        List.of(details[0]),
        List.of(details[1]),
        List.of(details[2]));
  }
}
