package com.codinglair.taf.mobile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Provisioned mobile device descriptor")
class DeviceDescriptorTest {
  @Nested
  @DisplayName("Boundary validation")
  class Validation {
    @Test
    @DisplayName("rejects a blank device identifier")
    void rejectsBlankIdentifier() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new DeviceDescriptor(
                  " ",
                  MobilePlatform.ANDROID,
                  DeviceMode.LOCAL_EMULATOR,
                  URI.create("http://localhost:4723"),
                  Map.of()));
    }

    @Test
    @DisplayName("takes an immutable snapshot of provider capabilities")
    void copiesCapabilities() {
      var values = new HashMap<String, Object>();
      values.put("model", "emulator");
      var descriptor =
          new DeviceDescriptor(
              "device",
              MobilePlatform.ANDROID,
              DeviceMode.LOCAL_EMULATOR,
              URI.create("http://localhost:4723"),
              values);
      values.put("model", "changed");
      assertThat(descriptor.capabilities()).containsEntry("model", "emulator");
    }
  }
}
