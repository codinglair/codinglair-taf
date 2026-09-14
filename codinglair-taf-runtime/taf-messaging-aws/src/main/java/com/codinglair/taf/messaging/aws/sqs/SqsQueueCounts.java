package com.codinglair.taf.messaging.aws.sqs;

import java.time.Instant;

/** Approximate SQS queue counts; these values are diagnostic and not exact assertion primitives. */
public record SqsQueueCounts(
    String queue, long available, long inFlight, long delayed, Instant collectedAt) {}
