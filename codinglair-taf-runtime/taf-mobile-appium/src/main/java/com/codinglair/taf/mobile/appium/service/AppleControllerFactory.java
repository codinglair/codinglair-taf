package com.codinglair.taf.mobile.appium.service;

@FunctionalInterface
public interface AppleControllerFactory {
  AppleController create(String name);
}
