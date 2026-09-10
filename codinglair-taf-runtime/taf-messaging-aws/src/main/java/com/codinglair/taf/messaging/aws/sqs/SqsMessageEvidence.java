package com.codinglair.taf.messaging.aws.sqs;

import com.codinglair.taf.messaging.aws.common.AwsEvidencePayload;
import java.time.Instant;
import java.util.Map;

/** Stable, receipt-handle-free evidence for an observed SQS message. */
public record SqsMessageEvidence(
    String messageId,
    String correlationId,
    int receiveCount,
    Instant sentAt,
    Instant receivedAt,
    Map<String, String> attributes,
    AwsEvidencePayload payload) {
  public SqsMessageEvidence {
    attributes = Map.copyOf(attributes);
  }
}
