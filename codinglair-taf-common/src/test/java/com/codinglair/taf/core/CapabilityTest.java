package com.codinglair.taf.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Unit tests for the core Capability value type (RT-001). */
public class CapabilityTest {

  @Test
  void testSuccessfulCapabilityCreation() {
    String name = "REST_CONTROLLER";
    String descriptor = "Controller for REST endpoints";
    Capability.CapabilityType type = Capability.CapabilityType.CONTROLLER;

    Capability capability = new Capability(name, descriptor, type);

    assertNotNull(capability);
    assertEquals(name, capability.name());
    assertEquals(descriptor, capability.descriptor());
    assertEquals(type, capability.type());
  }

  @Test
  void testCapabilityNameNullFails() {
    String descriptor = "A test descriptor";
    Capability.CapabilityType type = Capability.CapabilityType.RUNTIME_CORE;

    // Expect IllegalArgumentException when name is null
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new Capability(null, descriptor, type);
        },
        "Capability constructor should reject null name.");
  }

  @Test
  void testCapabilityDescriptorNullFails() {
    String name = "CONTROLLER_A";
    Capability.CapabilityType type = Capability.CapabilityType.CONTROLLER;

    // Expect IllegalArgumentException when descriptor is null
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new Capability(name, null, type);
        },
        "Capability constructor should reject null descriptor.");
  }
}
