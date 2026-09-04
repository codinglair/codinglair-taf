package com.codinglair.taf.mobile;

import java.net.URI;

/** Supplies an authorized Appium endpoint; controllers never start server processes. */
@FunctionalInterface
public interface AppiumServerProvider {
  URI endpoint(DeviceDescriptor device);
}
