package com.codinglair.taf.messaging.jms;

import com.codinglair.taf.runtime.core.controller.ControllerContext;
import jakarta.jms.ConnectionFactory;

/**
 * Creates a provider connection factory from named controller settings. EMS integrations implement
 * this SPI.
 */
@FunctionalInterface
public interface JmsConnectionFactoryProvider {
  ConnectionFactory create(
      String controllerName, JmsControllerSettings settings, ControllerContext context);
}
