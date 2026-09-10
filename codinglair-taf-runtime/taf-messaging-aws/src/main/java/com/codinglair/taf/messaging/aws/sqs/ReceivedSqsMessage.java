package com.codinglair.taf.messaging.aws.sqs;

import java.util.Objects;

/** Session-scoped message. The receipt handle must never be persisted or attached as evidence. */
public final class ReceivedSqsMessage {
  private final SqsMessage message;
  private final String receiptHandle;

  public ReceivedSqsMessage(SqsMessage message, String receiptHandle) {
    this.message = Objects.requireNonNull(message);
    this.receiptHandle = Objects.requireNonNull(receiptHandle);
  }

  public SqsMessage message() {
    return message;
  }

  /** Serialization-friendly message accessor. */
  public SqsMessage getMessage() {
    return message;
  }

  public String receiptHandle() {
    return receiptHandle;
  }

  /** Returns a safe representation for serializers that discover JavaBean properties. */
  public String getReceiptHandle() {
    return "<redacted>";
  }

  @Override
  public String toString() {
    return "ReceivedSqsMessage[message=" + message + ", receiptHandle=<redacted>]";
  }
}
