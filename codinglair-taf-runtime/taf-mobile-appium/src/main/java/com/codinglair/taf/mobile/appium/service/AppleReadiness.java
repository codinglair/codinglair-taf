package com.codinglair.taf.mobile.appium.service;

import com.codinglair.taf.mobile.MobileTopology;
import com.codinglair.taf.mobile.appium.configuration.AppleAuthentication.Mechanism;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.runtime.core.controller.HealthResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Passive, operator-supplied prerequisite diagnostics. Never runs host commands or opens sessions.
 */
public final class AppleReadiness {
  private AppleReadiness() {}

  public static HealthResult inspect(AppleControllerSettings settings) {
    var diagnostics = new LinkedHashMap<String, String>();
    try {
      settings.validate();
    } catch (IllegalArgumentException _) {
      diagnostics.put(
          "configuration", "Correct Apple platform, target, app, endpoint and WDA options");
    }
    if (settings.getAuthentication().getMechanism() != Mechanism.NONE)
      diagnostics.put(
          "authentication",
          "Resolve secret references through authorized SEC-130-001 transport integration");
    var required =
        new ArrayList<>(List.of("endpoint", "compatibility", "target", "application", "wda"));
    if (settings.getTopology() == MobileTopology.LOCAL_HOST)
      required.addAll(List.of("doctor", "xcode", "device", "signing"));
    boolean unavailable = !diagnostics.isEmpty();
    for (String dimension : required) {
      Boolean value = settings.getPrerequisites().get(dimension);
      if (value == null)
        diagnostics.put(
            dimension,
            "Unknown; supply current operator/provider metadata or confirm by authorized initialization");
      else if (!value) {
        unavailable = true;
        diagnostics.put(
            dimension,
            "Unavailable; repair the " + dimension + " prerequisite on the Appium host/provider");
      }
    }
    // Operator declarations cannot confirm a usable session, even when endpoint status is positive.
    if (diagnostics.isEmpty())
      diagnostics.put(
          "session", "Not initialized; confirm through authorized TestSession lifecycle");
    return new HealthResult(
        unavailable ? HealthResult.Status.UNAVAILABLE : HealthResult.Status.UNKNOWN,
        "Apple prerequisite readiness requires session confirmation",
        Map.copyOf(diagnostics));
  }
}
