package com.codinglair.taf.mobile;

import com.codinglair.taf.runtime.core.controller.TestController;
import java.nio.file.Path;
import java.time.Duration;

/** Platform-neutral native-mobile lifecycle and interaction contract. */
public interface MobileController<D, E> extends TestController {
  MobilePlatform platform();

  D nativeDriver();

  E find(String selector);

  void tap(E element);

  void type(E element, String text);

  void swipe(int startX, int startY, int endX, int endY, Duration duration);

  void longPress(E element, Duration duration);

  void setOrientation(MobileOrientation orientation);

  void install(Path application);

  void launch();

  void reset();

  void background(Duration duration);

  void terminate();

  void openDeepLink(String uri);

  void grantPermission(String permission);

  void revokePermission(String permission);

  void acceptDialog();

  void dismissDialog();

  byte[] screenshot();

  String pageSource();
}
