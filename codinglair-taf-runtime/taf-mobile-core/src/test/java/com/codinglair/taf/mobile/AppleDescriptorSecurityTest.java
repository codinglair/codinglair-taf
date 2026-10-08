package com.codinglair.taf.mobile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Apple provider descriptor credential isolation with Android compatibility")
class AppleDescriptorSecurityTest {
  DeviceDescriptor descriptor(MobilePlatform platform, Map<String, Object> options) {
    return new DeviceDescriptor(
        "device",
        platform,
        DeviceMode.LOCAL_EMULATOR,
        URI.create("http://localhost/custom"),
        options);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"accessKey", "password", "authorization", "api-key", "username", "user", "pwd"})
  @DisplayName("rejects plaintext credentials nested in lists without echoing the supplied value")
  void denied(String key) {
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                descriptor(
                    MobilePlatform.IOS,
                    Map.of(
                        "cloud:options",
                        Map.of("accounts", List.of(Map.of(key, "FAKE_DESCRIPTOR_CANARY"))))));
    assertThat(failure.toString()).doesNotContain("FAKE_DESCRIPTOR_CANARY");
  }

  @Test
  @DisplayName(
      "retains only detached reference objects and leaves Android descriptor behavior unchanged")
  void copy() {
    var accounts =
        new ArrayList<Object>(
            List.of(Map.of("accessKey", Map.of("secretReference", "secret://env/APPLE_KEY"))));
    var descriptor =
        descriptor(MobilePlatform.IOS, Map.of("cloud:options", Map.of("accounts", accounts)));
    accounts.clear();
    assertThat(descriptor.capabilities().toString()).contains("secret://env/APPLE_KEY");
    assertThat(
            descriptor(MobilePlatform.ANDROID, Map.of("vendor:key", "legacy-value")).capabilities())
        .containsEntry("vendor:key", "legacy-value");
  }
}
