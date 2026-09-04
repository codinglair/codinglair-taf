package com.codinglair.taf.mobile;

/**
 * Marker for platform-specific screens; shared business tasks depend on their own task contracts.
 */
public interface MobileScreen {
  MobilePlatform platform();
}
