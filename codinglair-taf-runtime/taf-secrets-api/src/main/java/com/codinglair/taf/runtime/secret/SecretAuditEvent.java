package com.codinglair.taf.runtime.secret;

import java.time.Instant;

/** Metadata-only secret access audit event. */
public record SecretAuditEvent(
    String provider,
    String referenceToken,
    String requestingComponent,
    String sessionId,
    String environment,
    String outcome,
    boolean authorized,
    Instant timestamp,
    long durationNanos) {}
