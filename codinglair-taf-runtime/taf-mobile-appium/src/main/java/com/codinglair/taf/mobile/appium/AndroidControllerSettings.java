package com.codinglair.taf.mobile.appium;

import com.codinglair.taf.mobile.ApplicationMode;
import com.codinglair.taf.mobile.DeviceMode;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

public class AndroidControllerSettings {
  private URI serverUrl;
  private String deviceName;
  private String deviceId;
  private DeviceMode deviceMode = DeviceMode.LOCAL_EMULATOR;
  private ApplicationMode applicationMode = ApplicationMode.PREINSTALLED;
  private String appPackage;
  private String appActivity;
  private Path app;
  private Duration commandTimeout = Duration.ofMinutes(2);
  private boolean uninstallPackagedAppOnClose = true;
  private boolean terminateAppOnClose = true;
  private boolean screenshotOnFailure = true;
  private boolean pageSourceOnFailure = true;
  private boolean deviceLogs = true;
  private boolean video;
  private boolean allowVisualArtifacts;

  public void validate(String prefix) {
    if (serverUrl == null
        || !java.util.Set.of("http", "https").contains(serverUrl.getScheme())
        || serverUrl.getHost() == null
        || serverUrl.getUserInfo() != null)
      throw new IllegalArgumentException(
          prefix + ".server-url must be an HTTP(S) endpoint without embedded credentials");
    if (deviceName == null || deviceName.isBlank())
      throw new IllegalArgumentException(prefix + ".device-name must not be blank");
    if (appPackage == null || appPackage.isBlank())
      throw new IllegalArgumentException(prefix + ".app-package must not be blank");
    if (applicationMode == ApplicationMode.PACKAGED && app == null)
      throw new IllegalArgumentException(prefix + ".app is required in packaged mode");
    if (commandTimeout == null || commandTimeout.isNegative() || commandTimeout.isZero())
      throw new IllegalArgumentException(prefix + ".command-timeout must be positive");
    if (video && !allowVisualArtifacts)
      throw new IllegalArgumentException(prefix + ".video requires allow-visual-artifacts=true");
  }

  public URI getServerUrl() {
    return serverUrl;
  }

  public void setServerUrl(URI v) {
    serverUrl = v;
  }

  public String getDeviceName() {
    return deviceName;
  }

  public void setDeviceName(String v) {
    deviceName = v;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(String v) {
    deviceId = v;
  }

  public DeviceMode getDeviceMode() {
    return deviceMode;
  }

  public void setDeviceMode(DeviceMode v) {
    deviceMode = v;
  }

  public ApplicationMode getApplicationMode() {
    return applicationMode;
  }

  public void setApplicationMode(ApplicationMode v) {
    applicationMode = v;
  }

  public String getAppPackage() {
    return appPackage;
  }

  public void setAppPackage(String v) {
    appPackage = v;
  }

  public String getAppActivity() {
    return appActivity;
  }

  public void setAppActivity(String v) {
    appActivity = v;
  }

  public Path getApp() {
    return app;
  }

  public void setApp(Path v) {
    app = v;
  }

  public Duration getCommandTimeout() {
    return commandTimeout;
  }

  public void setCommandTimeout(Duration v) {
    commandTimeout = v;
  }

  public boolean isUninstallPackagedAppOnClose() {
    return uninstallPackagedAppOnClose;
  }

  public void setUninstallPackagedAppOnClose(boolean v) {
    uninstallPackagedAppOnClose = v;
  }

  public boolean isTerminateAppOnClose() {
    return terminateAppOnClose;
  }

  public void setTerminateAppOnClose(boolean v) {
    terminateAppOnClose = v;
  }

  public boolean isScreenshotOnFailure() {
    return screenshotOnFailure;
  }

  public void setScreenshotOnFailure(boolean v) {
    screenshotOnFailure = v;
  }

  public boolean isPageSourceOnFailure() {
    return pageSourceOnFailure;
  }

  public void setPageSourceOnFailure(boolean v) {
    pageSourceOnFailure = v;
  }

  public boolean isDeviceLogs() {
    return deviceLogs;
  }

  public void setDeviceLogs(boolean v) {
    deviceLogs = v;
  }

  public boolean isVideo() {
    return video;
  }

  public void setVideo(boolean v) {
    video = v;
  }

  public boolean isAllowVisualArtifacts() {
    return allowVisualArtifacts;
  }

  public void setAllowVisualArtifacts(boolean v) {
    allowVisualArtifacts = v;
  }
}
