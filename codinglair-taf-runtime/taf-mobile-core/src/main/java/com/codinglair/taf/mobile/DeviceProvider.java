package com.codinglair.taf.mobile;

/** Infrastructure boundary for already-provisioned local or cloud devices. */
public interface DeviceProvider {
  DeviceDescriptor acquire(String profile);

  void release(DeviceDescriptor device);
}
