package com.codinglair.taf.virtualization.wiremock;

/** Explicit authority boundary for disruptive network-failure simulation. */
@FunctionalInterface
public interface NetworkFaultPolicy {
  boolean permits(String environmentName);

  static NetworkFaultPolicy denyAll() {
    return ignored -> false;
  }
}
