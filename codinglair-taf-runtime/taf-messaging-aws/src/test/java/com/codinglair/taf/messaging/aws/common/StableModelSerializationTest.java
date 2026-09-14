package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.aws.eventbridge.EventPublishEntryResult;
import com.codinglair.taf.messaging.aws.eventbridge.EventPublishResult;
import com.codinglair.taf.messaging.aws.sqs.ReceivedSqsMessage;
import com.codinglair.taf.messaging.aws.sqs.SqsMessage;
import com.codinglair.taf.messaging.aws.sqs.SqsSendResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StableModelSerializationTest {
  private final ObjectMapper mapper = mapper();

  @Test
  void sqsResultHasStableSdkFreeShape() throws Exception {
    String json = mapper.writeValueAsString(new SqsSendResult("m-1", "abc", Instant.EPOCH));
    assertThat(json)
        .isEqualTo(
            "{\"messageId\":\"m-1\",\"bodyDigest\":\"abc\",\"sentAt\":\"1970-01-01T00:00:00Z\"}");
  }

  @Test
  void eventResultHasStableSdkFreeShape() throws Exception {
    var entry = new EventPublishEntryResult(0, true, "e-1", null, null);
    String json = mapper.writeValueAsString(new EventPublishResult(List.of(entry), Instant.EPOCH));
    assertThat(json)
        .isEqualTo(
            "{\"entries\":[{\"index\":0,\"accepted\":true,\"eventId\":\"e-1\",\"errorCode\":null,\"errorMessage\":null}],\"publishedAt\":\"1970-01-01T00:00:00Z\"}");
  }

  @Test
  void receivedMessageSerializationRedactsReceiptHandle() throws Exception {
    var message =
        new ReceivedSqsMessage(
            new SqsMessage("m-1", "body", Map.of(), null, 1, Instant.EPOCH),
            "secret-receipt-handle");

    String json = mapper.writeValueAsString(message);

    assertThat(json)
        .contains("\"receiptHandle\":\"<redacted>\"")
        .contains("\"messageId\":\"m-1\"")
        .doesNotContain("secret-receipt-handle");
  }

  private static ObjectMapper mapper() {
    SimpleModule time = new SimpleModule();
    time.addSerializer(Instant.class, ToStringSerializer.instance);
    return new ObjectMapper().registerModule(time);
  }
}
