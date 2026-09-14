package com.codinglair.taf.messaging.aws.sqs;

/** Safe handling policy for messages that do not match a receive request. */
public enum SqsUnmatchedMessagePolicy {
  /** Immediately releases the message without deleting it. */
  RESTORE_IMMEDIATELY
}
