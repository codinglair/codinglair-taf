package com.codinglair.taf.mobile.appium;

@FunctionalInterface
public interface AndroidControllerFactory {
  AndroidController create(String name);
}
