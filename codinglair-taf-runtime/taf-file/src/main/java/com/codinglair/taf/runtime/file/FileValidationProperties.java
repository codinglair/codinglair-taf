package com.codinglair.taf.runtime.file;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("taf.file")
@Validated
public class FileValidationProperties {
  private boolean enabled;
  private final Map<String, Sandbox> sandboxes = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Map<String, Sandbox> getSandboxes() {
    return sandboxes;
  }

  public static class Sandbox {
    private Path root;
    private long maximumSize = 10 * 1024 * 1024;

    public Path getRoot() {
      return root;
    }

    public void setRoot(Path root) {
      this.root = root;
    }

    public long getMaximumSize() {
      return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
      this.maximumSize = maximumSize;
    }
  }
}
