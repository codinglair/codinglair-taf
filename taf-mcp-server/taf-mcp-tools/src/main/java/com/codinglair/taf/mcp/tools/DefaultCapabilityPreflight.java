package com.codinglair.taf.mcp.tools;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Immutable, fail-closed preflight for named capability requirements. */
public final class DefaultCapabilityPreflight implements CapabilityPreflight {
  private final Map<String, ConfiguredCapability> configured;

  public DefaultCapabilityPreflight(Collection<ConfiguredCapability> configured) {
    var values = new HashMap<String, ConfiguredCapability>();
    for (var capability : configured) {
      var previous = values.put(key(capability.capabilityId(), capability.instance()), capability);
      if (previous != null) throw new IllegalArgumentException("duplicate capability instance");
    }
    this.configured = Map.copyOf(values);
  }

  @Override
  public void validate(ToolRequest request) {
    for (var required : request.requiredCapabilities()) {
      var capability = configured.get(key(required.capabilityId(), required.instance()));
      if (capability == null || !capability.installed())
        fail("required capability is not installed");
      if (!capability.environment().equals(request.environment()))
        fail("capability environment is not authorized");
      if (!capability.ready()) fail("capability environment is not ready");
      if (!capability.permittedOperations().containsAll(required.operations()))
        fail("capability operation is not permitted");
      if (!compatible(capability)) fail("capability ownership and isolation are incompatible");
    }
  }

  private static boolean compatible(ConfiguredCapability capability) {
    return !("EXTERNAL".equals(capability.ownershipMode())
        && ("DEDICATED_RESOURCE".equals(capability.isolationMode())
            || "DEDICATED_NAMESPACE".equals(capability.isolationMode())));
  }

  private static String key(String capability, String instance) {
    return capability + '\u001f' + instance;
  }

  private static void fail(String message) {
    throw new IllegalArgumentException(message);
  }
}
