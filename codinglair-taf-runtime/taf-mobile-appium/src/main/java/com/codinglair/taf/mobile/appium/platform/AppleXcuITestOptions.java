package com.codinglair.taf.mobile.appium.platform;

import io.appium.java_client.ios.options.XCUITestOptions;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Preserve the approved Apple wire spelling despite Selenium's Platform enum normalization. */
final class AppleXcuITestOptions extends XCUITestOptions {
  @Override
  public Map<String, Object> asMap() {
    var capabilities = new LinkedHashMap<>(super.asMap());
    capabilities.put("platformName", "iOS");
    return Collections.unmodifiableMap(capabilities);
  }
}
