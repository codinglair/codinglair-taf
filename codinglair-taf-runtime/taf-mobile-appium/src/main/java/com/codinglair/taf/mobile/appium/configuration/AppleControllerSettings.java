package com.codinglair.taf.mobile.appium.configuration;

import com.codinglair.taf.mobile.ApplicationMode;
import com.codinglair.taf.mobile.MobileDeviceFamily;
import com.codinglair.taf.mobile.MobileDeviceKind;
import com.codinglair.taf.mobile.MobileExecutionMode;
import com.codinglair.taf.mobile.MobileTopology;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Nullable binding values preserve explicit overrides. Resolve before using a session. */
public class AppleControllerSettings {
  public enum LifecyclePolicy {
    REUSE,
    RELAUNCH,
    REINSTALL
  }

  public enum ReferenceKind {
    SERVER_PATH,
    AUTHORIZED_URL,
    PROVIDER_UPLOAD
  }

  public record AppReference(ReferenceKind kind, String value, MobileDeviceKind buildKind) {}

  private String platform;
  private MobileDeviceFamily deviceFamily;
  private MobileExecutionMode executionMode;
  private MobileDeviceKind deviceKind;
  private MobileTopology topology;
  private URI serverUrl;
  private String deviceName;
  private String deviceId;
  private String platformVersion;
  private String automationName;
  private ApplicationMode applicationMode;
  private AppReference appReference;
  private String bundleId;
  private Duration commandTimeout;
  private Duration readinessTimeout;
  private Duration contextTimeout;
  private Duration cleanupTimeout;
  private LifecyclePolicy lifecyclePolicy;
  private Boolean terminateAppOnClose;
  private Boolean uninstallPackagedAppOnClose;
  private Boolean screenshotOnFailure;
  private Boolean pageSourceOnFailure;
  private Boolean deviceLogs;
  private Boolean video;
  private Boolean requireEvidence;
  private Boolean allowVisualArtifacts;
  private Boolean autoAcceptAlerts;
  private Boolean autoDismissAlerts;
  private Map<String, Object> providerOptions = Map.of();
  private Map<String, Object> providerSelection = Map.of();
  private AppleAuthentication authentication = new AppleAuthentication();
  private AppleWdaSettings wda = new AppleWdaSettings();
  private Map<String, Boolean> prerequisites = Map.of();
  private String allocationResource;

  /** Name of an already authorized, provider-owned EnvironmentAccess resource. */
  public String getAllocationResource() {
    return allocationResource;
  }

  public void setAllocationResource(String value) {
    allocationResource = value;
  }

  /** Sanitized operator/provider declarations, never raw doctor output or signing material. */
  public Map<String, Boolean> getPrerequisites() {
    return prerequisites;
  }

  public void setPrerequisites(Map<String, Boolean> value) {
    var allowed =
        Set.of(
            "endpoint",
            "compatibility",
            "target",
            "application",
            "wda",
            "doctor",
            "xcode",
            "device",
            "signing");
    if (!allowed.containsAll(value.keySet())) throw invalid("unknown prerequisite dimension");
    prerequisites = Map.copyOf(value);
  }

  /**
   * Merge only overrides already authorized by the calling execution boundary. Does not grant
   * authorization.
   */
  public static AppleControllerSettings resolve(
      AppleControllerSettings base,
      AppleControllerSettings instance,
      AppleControllerSettings authorizedOverrides) {
    var result = merge(base, instance, authorizedOverrides);
    result.validate();
    return result;
  }

  /**
   * Detached binding snapshot; validation is deferred until explicit preflight or initialization.
   */
  public static AppleControllerSettings merge(
      AppleControllerSettings base,
      AppleControllerSettings instance,
      AppleControllerSettings authorizedOverrides) {
    var result = new AppleControllerSettings();
    for (var layer : new AppleControllerSettings[] {base, instance, authorizedOverrides}) {
      if (layer == null) continue;
      if (layer.platform != null) result.platform = layer.platform;
      if (layer.allocationResource != null) result.allocationResource = layer.allocationResource;
      if (layer.deviceFamily != null) result.deviceFamily = layer.deviceFamily;
      if (layer.executionMode != null) result.executionMode = layer.executionMode;
      if (layer.deviceKind != null) result.deviceKind = layer.deviceKind;
      if (layer.topology != null) result.topology = layer.topology;
      if (layer.serverUrl != null) result.serverUrl = layer.serverUrl;
      if (layer.deviceName != null) result.deviceName = layer.deviceName;
      if (layer.deviceId != null) result.deviceId = layer.deviceId;
      if (layer.platformVersion != null) result.platformVersion = layer.platformVersion;
      if (layer.automationName != null) result.automationName = layer.automationName;
      if (layer.applicationMode != null) result.applicationMode = layer.applicationMode;
      if (layer.appReference != null) {
        var previous = result.appReference;
        var next = layer.appReference;
        result.appReference =
            previous == null
                ? next
                : new AppReference(
                    next.kind() == null ? previous.kind() : next.kind(),
                    next.value() == null ? previous.value() : next.value(),
                    next.buildKind() == null ? previous.buildKind() : next.buildKind());
      }
      if (layer.bundleId != null) result.bundleId = layer.bundleId;
      if (layer.commandTimeout != null) result.commandTimeout = layer.commandTimeout;
      if (layer.readinessTimeout != null) result.readinessTimeout = layer.readinessTimeout;
      if (layer.contextTimeout != null) result.contextTimeout = layer.contextTimeout;
      if (layer.cleanupTimeout != null) result.cleanupTimeout = layer.cleanupTimeout;
      if (layer.lifecyclePolicy != null) result.lifecyclePolicy = layer.lifecyclePolicy;
      if (layer.terminateAppOnClose != null) result.terminateAppOnClose = layer.terminateAppOnClose;
      if (layer.uninstallPackagedAppOnClose != null)
        result.uninstallPackagedAppOnClose = layer.uninstallPackagedAppOnClose;
      if (layer.screenshotOnFailure != null) result.screenshotOnFailure = layer.screenshotOnFailure;
      if (layer.pageSourceOnFailure != null) result.pageSourceOnFailure = layer.pageSourceOnFailure;
      if (layer.deviceLogs != null) result.deviceLogs = layer.deviceLogs;
      if (layer.video != null) result.video = layer.video;
      if (layer.requireEvidence != null) result.requireEvidence = layer.requireEvidence;
      if (layer.allowVisualArtifacts != null)
        result.allowVisualArtifacts = layer.allowVisualArtifacts;
      if (layer.autoAcceptAlerts != null) result.autoAcceptAlerts = layer.autoAcceptAlerts;
      if (layer.autoDismissAlerts != null) result.autoDismissAlerts = layer.autoDismissAlerts;
      result.providerOptions = JsonOptions.merge(result.providerOptions, layer.providerOptions);
      result.providerSelection =
          JsonOptions.merge(result.providerSelection, layer.providerSelection);
      result.authentication =
          AppleAuthentication.merge(result.authentication, layer.authentication);
      result.wda = AppleWdaSettings.merge(result.wda, layer.wda);
      var metadata = new LinkedHashMap<>(result.prerequisites);
      metadata.putAll(layer.prerequisites);
      result.prerequisites = Map.copyOf(metadata);
    }
    return result;
  }

  public MobileDeviceFamily family() {
    if (getPlatform() == null) throw invalid("platform is required");
    MobileDeviceFamily inferred =
        switch (getPlatform().toLowerCase(Locale.ROOT)) {
          case "ios" -> MobileDeviceFamily.IPHONE;
          case "ipados" -> MobileDeviceFamily.IPAD;
          case "apple" -> getDeviceFamily();
          default -> throw invalid("platform must be ios, ipados or apple");
        };
    if (inferred == null || (deviceFamily != null && deviceFamily != inferred))
      throw invalid("device-family is required and must agree with platform");
    return inferred;
  }

  public void validate() {
    authentication.validate();
    wda.validate();
    family();
    if (!"XCUITest".equalsIgnoreCase(getAutomationName()))
      throw invalid("automation-name must be XCUITest");
    endpoint(getServerUrl());
    if (blank(getDeviceName()) || getDeviceKind() == null)
      throw invalid("device-name and device-kind are required");
    if (getDeviceKind() == MobileDeviceKind.PHYSICAL
        && blank(getDeviceId())
        && allocationResource == null
        && providerSelection.isEmpty())
      throw invalid("physical target requires device-id or provider-selection");
    if (!providerSelection.isEmpty() && getTopology() != MobileTopology.PROVIDER)
      throw invalid("provider-selection requires PROVIDER topology");
    for (Duration timeout :
        new Duration[] {
          getCommandTimeout(), getReadinessTimeout(), getContextTimeout(), getCleanupTimeout()
        })
      if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(10)) > 0)
        throw invalid("timeouts must be positive and at most ten minutes");
    if (getAutoAcceptAlerts() && getAutoDismissAlerts())
      throw invalid("alert policies are mutually exclusive");
    if (getVideo() && !getAllowVisualArtifacts())
      throw invalid("video requires allow-visual-artifacts");
    if (getExecutionMode() == MobileExecutionMode.SAFARI) {
      if (applicationMode != null
          || appReference != null
          || bundleId != null
          || terminateAppOnClose != null
          || uninstallPackagedAppOnClose != null)
        throw invalid("Safari forbids application installation and cleanup options");
      if (getLifecyclePolicy() == LifecyclePolicy.REINSTALL)
        throw invalid("Safari cannot reinstall an application");
    } else if (getApplicationMode() == ApplicationMode.PACKAGED) {
      if (appReference == null
          || appReference.kind() == null
          || blank(appReference.value())
          || appReference.buildKind() != getDeviceKind())
        throw invalid("packaged mode requires an explicit app reference with matching build-kind");
      switch (appReference.kind()) {
        case AUTHORIZED_URL -> {
          try {
            endpoint(URI.create(appReference.value()));
          } catch (IllegalArgumentException failure) {
            throw invalid("app URL must be an authorized HTTP(S) reference without credentials");
          }
        }
        case SERVER_PATH -> {
          if (!appReference.value().startsWith("/")
              || appReference.value().contains("..")
              || appReference.value().contains("\\"))
            throw invalid("app reference must be an absolute Apple server path without traversal");
        }
        case PROVIDER_UPLOAD -> {
          if (getTopology() != MobileTopology.PROVIDER)
            throw invalid("provider app reference requires PROVIDER topology");
        }
      }
    } else if (blank(bundleId) || appReference != null)
      throw invalid("preinstalled mode requires bundle-id and forbids app-reference");
    if (getLifecyclePolicy() == LifecyclePolicy.REINSTALL
        && getApplicationMode() != ApplicationMode.PACKAGED)
      throw invalid("REINSTALL requires packaged mode");
    JsonOptions.validateCapabilities(providerOptions);
    JsonOptions.validateCapabilities(providerSelection);
    JsonOptions.merge(providerOptions, providerSelection);
  }

  static void endpoint(URI uri) {
    if (uri == null
        || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getFragment() != null
        || uri.getRawQuery() != null
        || !uri.normalize().equals(uri)
        || uri.getRawPath().matches("(?i).*%(?:2e|2f|5c|25|0[0-9a-f]|1[0-9a-f]).*"))
      throw invalid("endpoint must be HTTP(S) without credentials, query or fragment");
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static IllegalArgumentException invalid(String reason) {
    return new IllegalArgumentException("Apple configuration: " + reason);
  }

  public Map<String, Object> getProviderOptions() {
    return providerOptions;
  }

  public void setProviderOptions(Map<String, Object> value) {
    providerOptions = JsonOptions.copy(value);
  }

  public Map<String, Object> getProviderSelection() {
    return providerSelection;
  }

  public Map<String, Object> providerCapabilities() {
    return JsonOptions.merge(providerOptions, providerSelection);
  }

  public void setProviderSelection(Map<String, Object> value) {
    providerSelection = JsonOptions.copy(value);
  }

  public AppleAuthentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(AppleAuthentication value) {
    authentication = Objects.requireNonNull(value);
  }

  public AppleWdaSettings getWda() {
    return wda;
  }

  public void setWda(AppleWdaSettings value) {
    wda = Objects.requireNonNull(value);
  }

  public String getPlatform() {
    return platform == null ? "apple" : platform;
  }

  public void setPlatform(String value) {
    platform = value;
  }

  public MobileDeviceFamily getDeviceFamily() {
    return deviceFamily == null ? null : deviceFamily;
  }

  public void setDeviceFamily(MobileDeviceFamily value) {
    deviceFamily = value;
  }

  public MobileExecutionMode getExecutionMode() {
    return executionMode == null ? MobileExecutionMode.NATIVE : executionMode;
  }

  public void setExecutionMode(MobileExecutionMode value) {
    executionMode = value;
  }

  public MobileDeviceKind getDeviceKind() {
    return deviceKind == null ? null : deviceKind;
  }

  public void setDeviceKind(MobileDeviceKind value) {
    deviceKind = value;
  }

  public MobileTopology getTopology() {
    return topology == null ? MobileTopology.REMOTE_HOST : topology;
  }

  public void setTopology(MobileTopology value) {
    topology = value;
  }

  public URI getServerUrl() {
    return serverUrl == null ? null : serverUrl;
  }

  public void setServerUrl(URI value) {
    serverUrl = value;
  }

  public String getDeviceName() {
    return deviceName == null ? null : deviceName;
  }

  public void setDeviceName(String value) {
    deviceName = value;
  }

  public String getDeviceId() {
    return deviceId == null ? null : deviceId;
  }

  public void setDeviceId(String value) {
    deviceId = value;
  }

  public String getPlatformVersion() {
    return platformVersion == null ? null : platformVersion;
  }

  public void setPlatformVersion(String value) {
    platformVersion = value;
  }

  public String getAutomationName() {
    return automationName == null ? "XCUITest" : automationName;
  }

  public void setAutomationName(String value) {
    automationName = value;
  }

  public ApplicationMode getApplicationMode() {
    return applicationMode == null ? ApplicationMode.PREINSTALLED : applicationMode;
  }

  public void setApplicationMode(ApplicationMode value) {
    applicationMode = value;
  }

  public AppReference getAppReference() {
    return appReference == null ? null : appReference;
  }

  public void setAppReference(AppReference value) {
    appReference = value;
  }

  public String getBundleId() {
    return bundleId == null ? null : bundleId;
  }

  public void setBundleId(String value) {
    bundleId = value;
  }

  public Duration getCommandTimeout() {
    return commandTimeout == null ? Duration.ofMinutes(2) : commandTimeout;
  }

  public void setCommandTimeout(Duration value) {
    commandTimeout = value;
  }

  public Duration getReadinessTimeout() {
    return readinessTimeout == null ? Duration.ofSeconds(30) : readinessTimeout;
  }

  public void setReadinessTimeout(Duration value) {
    readinessTimeout = value;
  }

  public Duration getContextTimeout() {
    return contextTimeout == null ? Duration.ofSeconds(30) : contextTimeout;
  }

  public void setContextTimeout(Duration value) {
    contextTimeout = value;
  }

  public Duration getCleanupTimeout() {
    return cleanupTimeout == null ? Duration.ofSeconds(30) : cleanupTimeout;
  }

  public void setCleanupTimeout(Duration value) {
    cleanupTimeout = value;
  }

  public LifecyclePolicy getLifecyclePolicy() {
    return lifecyclePolicy == null ? LifecyclePolicy.RELAUNCH : lifecyclePolicy;
  }

  public void setLifecyclePolicy(LifecyclePolicy value) {
    lifecyclePolicy = value;
  }

  public Boolean getTerminateAppOnClose() {
    return terminateAppOnClose == null ? true : terminateAppOnClose;
  }

  public void setTerminateAppOnClose(Boolean value) {
    terminateAppOnClose = value;
  }

  public Boolean getUninstallPackagedAppOnClose() {
    return uninstallPackagedAppOnClose == null ? false : uninstallPackagedAppOnClose;
  }

  public void setUninstallPackagedAppOnClose(Boolean value) {
    uninstallPackagedAppOnClose = value;
  }

  public Boolean getScreenshotOnFailure() {
    return screenshotOnFailure == null ? true : screenshotOnFailure;
  }

  public void setScreenshotOnFailure(Boolean value) {
    screenshotOnFailure = value;
  }

  public Boolean getPageSourceOnFailure() {
    return pageSourceOnFailure == null ? true : pageSourceOnFailure;
  }

  public void setPageSourceOnFailure(Boolean value) {
    pageSourceOnFailure = value;
  }

  public Boolean getDeviceLogs() {
    return deviceLogs == null ? true : deviceLogs;
  }

  public void setDeviceLogs(Boolean value) {
    deviceLogs = value;
  }

  public Boolean getVideo() {
    return video == null ? false : video;
  }

  /** Explicit strict policy: requested evidence must be available, independently of readiness. */
  public Boolean getRequireEvidence() {
    return requireEvidence == null ? false : requireEvidence;
  }

  public void setRequireEvidence(Boolean value) {
    requireEvidence = value;
  }

  public void setVideo(Boolean value) {
    video = value;
  }

  public Boolean getAllowVisualArtifacts() {
    return allowVisualArtifacts == null ? false : allowVisualArtifacts;
  }

  public void setAllowVisualArtifacts(Boolean value) {
    allowVisualArtifacts = value;
  }

  public Boolean getAutoAcceptAlerts() {
    return autoAcceptAlerts == null ? false : autoAcceptAlerts;
  }

  public void setAutoAcceptAlerts(Boolean value) {
    autoAcceptAlerts = value;
  }

  public Boolean getAutoDismissAlerts() {
    return autoDismissAlerts == null ? false : autoDismissAlerts;
  }

  public void setAutoDismissAlerts(Boolean value) {
    autoDismissAlerts = value;
  }
}
