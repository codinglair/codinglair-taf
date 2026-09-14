package com.codinglair.taf.messaging.aws.common;

/** Declares how a test flow is isolated from other consumers of an SQS resource. */
public enum SqsIsolationMode {
  /** A queue provisioned exclusively for the owning test scope. */
  DEDICATED_RESOURCE,
  /** A shared queue whose messages use a namespace unique to the owning test scope. */
  DEDICATED_NAMESPACE,
  /** A shared queue accessed by a consumer whose selection rules are controlled by the test. */
  CONTROLLED_CONSUMER,
  /** A non-destructive queue that mirrors messages from the system under test. */
  MIRROR_QUEUE,
  /** An operator-owned shared queue requiring non-destructive test behavior. */
  EXTERNAL_SHARED
}
