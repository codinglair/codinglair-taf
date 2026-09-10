package com.codinglair.taf.messaging.aws.sqs;

import java.time.Instant;

public record SqsSendResult(String messageId, String bodyDigest, Instant sentAt) {}
