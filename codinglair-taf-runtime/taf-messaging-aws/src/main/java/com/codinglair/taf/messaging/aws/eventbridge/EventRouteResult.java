package com.codinglair.taf.messaging.aws.eventbridge;

import com.codinglair.taf.messaging.aws.sqs.SqsMessageEvidence;

/** Stable proof that an accepted EventBridge event reached the configured SQS target. */
public record EventRouteResult(
    EventPublishEntryResult publishResult,
    String targetIdentity,
    String correlationId,
    SqsMessageEvidence targetEvidence) {}
