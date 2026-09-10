package com.codinglair.taf.messaging.aws.common;

/** Bounded, sanitized payload representation suitable for stable evidence models. */
public record AwsEvidencePayload(
    String content, String sha256, int originalBytes, boolean truncated) {}
