package com.codinglair.taf.mobile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Authoritative, side-effect-free Apple platform discovery and selection validation. */
public final class ApplePlatformManifest {
  public static final String CAPABILITY_ID = "mobile.apple";
  public static final String AUTOMATION_NAME = "XCUITest";
  public static final String WIRE_PLATFORM = "iOS";

  private static final Set<String> MODES = Set.of("NATIVE", "HYBRID", "SAFARI");
  private static final Set<String> KINDS = Set.of("SIMULATOR", "PHYSICAL");
  private static final Set<String> TOPOLOGIES = Set.of("LOCAL_HOST", "REMOTE_HOST", "PROVIDER");
  private static final Set<String> APPLICATION_MODES = Set.of("PREINSTALLED", "PACKAGED");

  private ApplePlatformManifest() {}

  public static Descriptor descriptor() {
    return new Descriptor(
        CAPABILITY_ID,
        WIRE_PLATFORM,
        AUTOMATION_NAME,
        List.of("IPHONE", "IPAD"),
        MODES.stream().sorted().toList(),
        KINDS.stream().sorted().toList(),
        List.of("screenshot", "source", "syslog", "video"),
        List.of(
            "External prepared Appium/XCUITest infrastructure is required",
            "Artifact availability is conditional",
            "Passive discovery does not allocate a target or create a session"),
        Map.of("video", "conditional", "syslog", "conditional"));
  }

  public static Selection validate(
      String platform,
      String family,
      String mode,
      String kind,
      String topology,
      String applicationMode,
      String automationName) {
    String normalizedPlatform = token(platform);
    String normalizedFamily = token(family);
    MobileDeviceFamily selectedFamily =
        switch (normalizedPlatform) {
          case "IOS" -> MobileDeviceFamily.IPHONE;
          case "IPADOS" -> MobileDeviceFamily.IPAD;
          case "APPLE" -> parseFamily(normalizedFamily);
          case null, default -> throw invalid("platform must be ios, ipados or apple");
        };
    if (normalizedFamily != null && selectedFamily != parseFamily(normalizedFamily)) {
      throw invalid("device family must agree with platform");
    }
    String selectedMode = supported(mode, "NATIVE", MODES, "execution mode");
    String selectedKind = supported(kind, "SIMULATOR", KINDS, "device kind");
    String selectedTopology = supported(topology, "REMOTE_HOST", TOPOLOGIES, "topology");
    String selectedAutomation = automationName == null ? AUTOMATION_NAME : automationName.trim();
    if (!AUTOMATION_NAME.equalsIgnoreCase(selectedAutomation)) {
      throw invalid("automation name must be XCUITest");
    }
    String selectedApplicationMode = token(applicationMode);
    if ("SAFARI".equals(selectedMode)) {
      if (selectedApplicationMode != null) {
        throw invalid("Safari forbids application mode");
      }
    } else {
      selectedApplicationMode =
          supported(selectedApplicationMode, "PREINSTALLED", APPLICATION_MODES, "application mode");
    }
    return new Selection(
        selectedFamily,
        selectedMode,
        selectedKind,
        selectedTopology,
        selectedApplicationMode,
        AUTOMATION_NAME,
        WIRE_PLATFORM);
  }

  private static MobileDeviceFamily parseFamily(String family) {
    return switch (family) {
      case "IPHONE" -> MobileDeviceFamily.IPHONE;
      case "IPAD" -> MobileDeviceFamily.IPAD;
      case null -> throw invalid("apple platform requires IPHONE or IPAD family");
      default -> throw invalid("device family must be IPHONE or IPAD");
    };
  }

  private static String supported(
      String value, String fallback, Set<String> allowed, String field) {
    String selected = token(value);
    selected = selected == null ? fallback : selected;
    if (!allowed.contains(selected)) throw invalid(field + " is unsupported");
    return selected;
  }

  private static String token(String value) {
    return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  private static IllegalArgumentException invalid(String reason) {
    return new IllegalArgumentException("Apple selection: " + reason);
  }

  public record Descriptor(
      String capabilityId,
      String wirePlatform,
      String automationName,
      List<String> families,
      List<String> modes,
      List<String> targetKinds,
      List<String> artifacts,
      List<String> limitations,
      Map<String, String> conditionalArtifacts) {
    public Descriptor {
      Objects.requireNonNull(capabilityId, "capabilityId");
      families = List.copyOf(families);
      modes = List.copyOf(modes);
      targetKinds = List.copyOf(targetKinds);
      artifacts = List.copyOf(artifacts);
      limitations = List.copyOf(limitations);
      conditionalArtifacts = Map.copyOf(conditionalArtifacts);
    }
  }

  public record Selection(
      MobileDeviceFamily family,
      String mode,
      String targetKind,
      String topology,
      String applicationMode,
      String automationName,
      String wirePlatform) {}
}
