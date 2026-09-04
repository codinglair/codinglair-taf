package com.codinglair.taf.web.playwright;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Legacy default settings plus typed settings for named controller instances. */
@ConfigurationProperties("taf.web.playwright")
public class PlaywrightProperties extends PlaywrightControllerSettings {
  public enum Engine {
    CHROMIUM,
    FIREFOX,
    WEBKIT
  }

  public enum Mode {
    LOCAL,
    REMOTE
  }

  private boolean enabled;
  private final Map<String, PlaywrightControllerSettings> controllers = new LinkedHashMap<>();

  public void validate() {
    validate("taf.web.playwright");
    controllers.forEach(
        (name, settings) -> {
          if (name == null || name.isBlank())
            throw new IllegalArgumentException("Playwright controller name must not be blank");
          settings.validate("taf.web.playwright.controllers." + name);
        });
  }

  public PlaywrightControllerSettings settings(String name) {
    PlaywrightControllerSettings named = controllers.get(name);
    return named == null ? this : named;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Map<String, PlaywrightControllerSettings> getControllers() {
    return controllers;
  }

  public static class Evidence {
    private boolean screenshotOnFailure = true;
    private boolean domOnFailure = true;
    private boolean trace;
    private boolean video;
    private boolean consoleErrors = true;
    private boolean networkErrors = true;
    private boolean allowVisualArtifacts;
    private Path directory = Path.of("target", "playwright-evidence");

    public boolean isScreenshotOnFailure() {
      return screenshotOnFailure;
    }

    public void setScreenshotOnFailure(boolean value) {
      screenshotOnFailure = value;
    }

    public boolean isDomOnFailure() {
      return domOnFailure;
    }

    public void setDomOnFailure(boolean value) {
      domOnFailure = value;
    }

    public boolean isTrace() {
      return trace;
    }

    public void setTrace(boolean value) {
      trace = value;
    }

    public boolean isVideo() {
      return video;
    }

    public void setVideo(boolean value) {
      video = value;
    }

    public boolean isConsoleErrors() {
      return consoleErrors;
    }

    public void setConsoleErrors(boolean value) {
      consoleErrors = value;
    }

    public boolean isNetworkErrors() {
      return networkErrors;
    }

    public void setNetworkErrors(boolean value) {
      networkErrors = value;
    }

    public boolean isAllowVisualArtifacts() {
      return allowVisualArtifacts;
    }

    public void setAllowVisualArtifacts(boolean value) {
      allowVisualArtifacts = value;
    }

    public Path getDirectory() {
      return directory;
    }

    public void setDirectory(Path value) {
      directory = value;
    }
  }
}
