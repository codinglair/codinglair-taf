package com.codinglair.taf.messaging.aws.sqs;

import com.codinglair.taf.messaging.aws.common.AwsOperationPolicy;
import com.codinglair.taf.messaging.aws.common.AwsOwnershipMode;
import com.codinglair.taf.messaging.aws.common.SqsIsolationMode;

public class SqsControllerProperties {
  private String queue;
  private String deadLetterQueue;
  private SqsIsolationMode isolationMode = SqsIsolationMode.DEDICATED_RESOURCE;

  public String getQueue() {
    return queue;
  }

  public void setQueue(String value) {
    queue = value;
  }

  public String getDeadLetterQueue() {
    return deadLetterQueue;
  }

  public void setDeadLetterQueue(String value) {
    deadLetterQueue = value;
  }

  public SqsIsolationMode getIsolationMode() {
    return isolationMode;
  }

  public void setIsolationMode(SqsIsolationMode value) {
    isolationMode = value;
  }

  public void validate(String path, AwsOwnershipMode ownership) {
    if (queue == null || queue.isBlank()) AwsOperationPolicy.fail(path + ".queue", "is required");
    if (isolationMode == null) AwsOperationPolicy.fail(path + ".isolation-mode", "is required");
    if (ownership == AwsOwnershipMode.EXTERNAL
        && (isolationMode == SqsIsolationMode.DEDICATED_RESOURCE
            || isolationMode == SqsIsolationMode.DEDICATED_NAMESPACE))
      AwsOperationPolicy.fail(
          path + ".isolation-mode", "cannot claim dedicated ownership for an external resource");
  }
}
