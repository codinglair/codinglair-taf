package com.codinglair.taf.mobile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApplePlatformManifestTest {
  @Test
  void normalizesAliasesWithoutAllocatingInfrastructure() {
    var ipad = ApplePlatformManifest.validate("ipados", null, null, null, null, null, null);
    var iphone =
        ApplePlatformManifest.validate(
            "apple", "iphone", "hybrid", "physical", "provider", "packaged", "xcuitest");

    assertThat(ipad.family()).isEqualTo(MobileDeviceFamily.IPAD);
    assertThat(ipad.wirePlatform()).isEqualTo("iOS");
    assertThat(iphone.family()).isEqualTo(MobileDeviceFamily.IPHONE);
    assertThat(iphone.mode()).isEqualTo("HYBRID");
  }

  @Test
  void rejectsConflictsAndSafariApplicationState() {
    assertThatThrownBy(
            () -> ApplePlatformManifest.validate("ios", "ipad", null, null, null, null, "XCUITest"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("agree");
    assertThatThrownBy(
            () ->
                ApplePlatformManifest.validate(
                    "ios", null, "safari", null, null, "preinstalled", null))
        .hasMessageContaining("Safari");
  }

  @Test
  void describesConditionalSupportSeparatelyFromReadiness() {
    var descriptor = ApplePlatformManifest.descriptor();

    assertThat(descriptor.capabilityId()).isEqualTo("mobile.apple");
    assertThat(descriptor.conditionalArtifacts()).containsEntry("video", "conditional");
    assertThat(descriptor.limitations()).anyMatch(value -> value.contains("does not allocate"));
  }
}
