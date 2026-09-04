package com.codinglair.taf.messaging.jms;

@FunctionalInterface
public interface JmsControllerFactory {
  JmsController create(String name);
}
