package com.codinglair.taf.mobile.appium.configuration;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** Service-side WDA identifiers only; no provisioning or signing material. */
public class AppleWdaSettings {
  public enum BuildMode {
    BUILD,
    PREBUILT,
    PREINSTALLED,
    RUNNING
  }

  private BuildMode buildMode;

  public BuildMode getBuildMode() {
    return buildMode == null ? BuildMode.BUILD : buildMode;
  }

  public void setBuildMode(BuildMode value) {
    buildMode = value;
  }

  private Integer localPort;

  public Integer getLocalPort() {
    return localPort;
  }

  public void setLocalPort(Integer value) {
    localPort = value;
  }

  private Integer mjpegPort;

  public Integer getMjpegPort() {
    return mjpegPort;
  }

  public void setMjpegPort(Integer value) {
    mjpegPort = value;
  }

  private String derivedDataPath;

  public String getDerivedDataPath() {
    return derivedDataPath;
  }

  public void setDerivedDataPath(String value) {
    derivedDataPath = value;
  }

  private String signingTeamId;

  public String getSigningTeamId() {
    return signingTeamId;
  }

  public void setSigningTeamId(String value) {
    signingTeamId = value;
  }

  private String signingIdentity;

  public String getSigningIdentity() {
    return signingIdentity;
  }

  public void setSigningIdentity(String value) {
    signingIdentity = value;
  }

  private String bundleId;

  public String getBundleId() {
    return bundleId;
  }

  public void setBundleId(String value) {
    bundleId = value;
  }

  private String prebuiltPath;

  public String getPrebuiltPath() {
    return prebuiltPath;
  }

  public void setPrebuiltPath(String value) {
    prebuiltPath = value;
  }

  private URI baseUrl;

  public URI getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(URI value) {
    baseUrl = value;
  }

  static AppleWdaSettings merge(AppleWdaSettings base, AppleWdaSettings overlay) {
    var result = new AppleWdaSettings();
    result.buildMode = overlay.buildMode == null ? base.buildMode : overlay.buildMode;
    result.localPort = overlay.localPort == null ? base.localPort : overlay.localPort;
    result.mjpegPort = overlay.mjpegPort == null ? base.mjpegPort : overlay.mjpegPort;
    result.derivedDataPath =
        overlay.derivedDataPath == null ? base.derivedDataPath : overlay.derivedDataPath;
    result.signingTeamId =
        overlay.signingTeamId == null ? base.signingTeamId : overlay.signingTeamId;
    result.signingIdentity =
        overlay.signingIdentity == null ? base.signingIdentity : overlay.signingIdentity;
    result.bundleId = overlay.bundleId == null ? base.bundleId : overlay.bundleId;
    result.prebuiltPath = overlay.prebuiltPath == null ? base.prebuiltPath : overlay.prebuiltPath;
    result.baseUrl = overlay.baseUrl == null ? base.baseUrl : overlay.baseUrl;
    return result;
  }

  void validate() {
    for (Integer port : new Integer[] {localPort, mjpegPort})
      if (port != null && (port < 1 || port > 65535))
        throw new IllegalArgumentException("Apple WDA port must be 1-65535");
    if (localPort != null && localPort.equals(mjpegPort))
      throw new IllegalArgumentException("Apple WDA and MJPEG ports must differ");
    if (getBuildMode() == BuildMode.PREBUILT && (prebuiltPath == null || prebuiltPath.isBlank()))
      throw new IllegalArgumentException("Apple PREBUILT WDA requires a server path");
    if (getBuildMode() == BuildMode.RUNNING) AppleControllerSettings.endpoint(baseUrl);
    for (String path : new String[] {prebuiltPath, derivedDataPath})
      if (path != null && (!path.startsWith("/") || path.contains("..") || path.contains("\\")))
        throw new IllegalArgumentException(
            "Apple WDA paths must be absolute server paths without traversal");
  }

  public Map<String, Object> capabilities() {
    validate();
    var result = new LinkedHashMap<String, Object>();
    if (localPort != null) result.put("appium:wdaLocalPort", localPort);
    if (mjpegPort != null) result.put("appium:mjpegServerPort", mjpegPort);
    if (derivedDataPath != null) result.put("appium:derivedDataPath", derivedDataPath);
    if (signingTeamId != null) result.put("appium:xcodeOrgId", signingTeamId);
    if (signingIdentity != null) result.put("appium:xcodeSigningId", signingIdentity);
    if (bundleId != null) result.put("appium:updatedWDABundleId", bundleId);
    if (prebuiltPath != null) result.put("appium:prebuiltWDAPath", prebuiltPath);
    if (baseUrl != null) result.put("appium:webDriverAgentUrl", baseUrl.toString());
    switch (getBuildMode()) {
      case PREBUILT -> result.put("appium:usePrebuiltWDA", true);
      case PREINSTALLED -> result.put("appium:usePreinstalledWDA", true);
      case BUILD, RUNNING -> {}
    }
    return Map.copyOf(result);
  }
}
