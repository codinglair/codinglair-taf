package com.codinglair.taf.runtime.core.controller;

/** Observable controller lifecycle state. */
public enum ControllerState {
  NEW,
  INITIALIZING,
  READY,
  FAILED,
  CLOSED
}
