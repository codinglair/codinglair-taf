package com.codinglair.taf.messaging.aws.eventbridge;

import com.codinglair.taf.messaging.aws.common.AwsOperationPolicy;

public class EventBridgeControllerProperties {
  private String eventBus;
  private String targetSqsController;
  private String targetIdentity;
  private String envelopeSchema;

  public String getEventBus() {
    return eventBus;
  }

  public void setEventBus(String value) {
    eventBus = value;
  }

  public String getTargetSqsController() {
    return targetSqsController;
  }

  public void setTargetSqsController(String value) {
    targetSqsController = value;
  }

  public String getTargetIdentity() {
    return targetIdentity;
  }

  public void setTargetIdentity(String value) {
    targetIdentity = value;
  }

  public String getEnvelopeSchema() {
    return envelopeSchema;
  }

  public void setEnvelopeSchema(String value) {
    envelopeSchema = value;
  }

  public void validate(String path) {
    if (eventBus == null || eventBus.isBlank())
      AwsOperationPolicy.fail(path + ".event-bus", "is required");
    if ((targetSqsController == null) != (targetIdentity == null))
      AwsOperationPolicy.fail(
          path, "target-sqs-controller and target-identity must be configured together");
    if (targetSqsController != null && (targetSqsController.isBlank() || targetIdentity.isBlank()))
      AwsOperationPolicy.fail(path, "target-sqs-controller and target-identity must not be blank");
  }
}
