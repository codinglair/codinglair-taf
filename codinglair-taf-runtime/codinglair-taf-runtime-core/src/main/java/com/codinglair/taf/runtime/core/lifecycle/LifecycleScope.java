package com.codinglair.taf.runtime.core.lifecycle;

/**
 * Lifecycle scope defines the lifetime and cleanup behavior of test resources.
 *
 * <p>Scopes:
 *
 * <ul>
 *   <li><b>TEST</b> - Resource lives for a single test method
 *   <li><b>SUITE</b> - Resource lives for the entire test suite
 *   <li><b>OPERATION</b> - Resource lives for a specific operation within a test
 *   <li><b>SHARED</b> - Resource is shared across tests (e.g., database connection)
 *   <li><b>ISOLATED</b> - Resource is isolated per test invocation
 * </ul>
 *
 * @author Codinglair TAF Team
 */
public enum LifecycleScope {

  /** Test scope - resource lives for a single test method. */
  TEST,

  /** Suite scope - resource lives for the entire test suite. */
  SUITE,

  /** Operation scope - resource lives for a specific operation within a test. */
  OPERATION,

  /** Shared scope - resource is shared across tests (e.g., database connection). */
  SHARED,

  /** Isolated scope - resource is isolated per test invocation. */
  ISOLATED;

  /**
   * Gets the cleanup priority for this scope. Lower values are cleaned up first; reverse order
   * during cleanup.
   *
   * @return cleanup priority (higher = cleaned up later)
   */
  public int getCleanupPriority() {
    return switch (this) {
      case TEST -> 1;
      case OPERATION -> 2;
      case ISOLATED -> 3;
      case SHARED -> 4;
      case SUITE -> 5;
      default -> 0;
    };
  }

  /**
   * Checks if this scope is suitable for temporary resources.
   *
   * @return true if the scope is temporary (TEST or OPERATION)
   */
  public boolean isTemporary() {
    return this == TEST || this == OPERATION;
  }

  /**
   * Checks if this scope is suitable for shared resources.
   *
   * @return true if the scope is shared (SHARED)
   */
  public boolean isShared() {
    return this == SHARED;
  }

  /**
   * Checks if this scope is suitable for isolated resources.
   *
   * @return true if the scope is isolated (ISOLATED)
   */
  public boolean isIsolated() {
    return this == ISOLATED;
  }
}
