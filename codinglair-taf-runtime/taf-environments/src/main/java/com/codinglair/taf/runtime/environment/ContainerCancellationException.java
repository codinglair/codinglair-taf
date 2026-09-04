package com.codinglair.taf.runtime.environment;

/**
 * Internal cancellation signal preserved as an environment provisioning failure at the provider
 * boundary.
 */
final class ContainerCancellationException extends RuntimeException {
  ContainerCancellationException(String message) {
    super(message);
  }
}
