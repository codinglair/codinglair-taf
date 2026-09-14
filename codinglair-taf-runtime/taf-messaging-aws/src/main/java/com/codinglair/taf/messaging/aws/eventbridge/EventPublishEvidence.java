package com.codinglair.taf.messaging.aws.eventbridge;

import com.codinglair.taf.messaging.aws.common.AwsEvidencePayload;
import java.util.List;
import java.util.Map;

/** Sanitized, bounded request evidence associated with one stable publish-result index. */
public record EventPublishEvidence(
    int index,
    String source,
    String detailType,
    List<String> resources,
    Map<String, String> metadata,
    String correlationId,
    AwsEvidencePayload detail) {
  public EventPublishEvidence {
    resources = List.copyOf(resources);
    metadata = Map.copyOf(metadata);
  }
}
