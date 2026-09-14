package com.codinglair.taf.messaging.aws.eventbridge;

public record EventPublishEntryResult(
    int index, boolean accepted, String eventId, String errorCode, String errorMessage) {}
