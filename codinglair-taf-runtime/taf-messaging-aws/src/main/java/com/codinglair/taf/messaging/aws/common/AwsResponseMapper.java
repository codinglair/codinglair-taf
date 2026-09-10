package com.codinglair.taf.messaging.aws.common;

import com.codinglair.taf.messaging.aws.eventbridge.EventPublishEntryResult;
import com.codinglair.taf.messaging.aws.sqs.ReceivedSqsMessage;
import com.codinglair.taf.messaging.aws.sqs.SqsMessage;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;
import software.amazon.awssdk.services.sqs.model.Message;

/** Internal one-way mapping boundary from SDK responses to stable framework models. */
final class AwsResponseMapper {
  private AwsResponseMapper() {}

  static EventPublishEntryResult eventEntry(int index, PutEventsResultEntry value) {
    return new EventPublishEntryResult(
        index,
        value.errorCode() == null,
        value.eventId(),
        value.errorCode(),
        value.errorMessage() == null
            ? null
            : AwsEvidenceSanitizer.payload(value.errorMessage(), 512).content());
  }

  static ReceivedSqsMessage message(Message value, Instant receivedAt) {
    int count =
        Integer.parseInt(value.attributesAsStrings().getOrDefault("ApproximateReceiveCount", "1"));
    Map<String, String> attributes = attributes(value);
    return new ReceivedSqsMessage(
        new SqsMessage(
            value.messageId(),
            value.body(),
            attributes,
            attributes.get("correlationId"),
            count,
            receivedAt),
        value.receiptHandle());
  }

  static Map<String, String> attributes(Message value) {
    Map<String, String> attributes = new LinkedHashMap<>();
    value.messageAttributes().forEach((key, item) -> attributes.put(key, item.stringValue()));
    return Map.copyOf(attributes);
  }
}
