package com.codinglair.taf.messaging.aws.common;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.eventbridge.model.EventBridgeException;
import software.amazon.awssdk.services.sqs.model.SqsException;

/** Maps implementation exceptions to stable, retry-aware framework categories. */
public final class AwsFailureClassifier {
  private AwsFailureClassifier() {}

  public static Classification classify(Throwable failure) {
    Throwable cause = root(failure);
    if (cause instanceof SocketTimeoutException || cause instanceof TimeoutException)
      return new Classification(AwsFailureCategory.TIMEOUT, true);
    if (cause instanceof ConnectException || cause instanceof SdkClientException)
      return new Classification(AwsFailureCategory.TRANSIENT_SERVICE, true);
    Integer status = statusCode(cause);
    if (status == null) return new Classification(AwsFailureCategory.AUTOMATION, false);
    return switch (status) {
      case 401 -> new Classification(AwsFailureCategory.AUTHENTICATION, false);
      case 403 -> new Classification(AwsFailureCategory.AUTHORIZATION, false);
      case 404 -> new Classification(AwsFailureCategory.MISSING_RESOURCE, false);
      case 408, 504 -> new Classification(AwsFailureCategory.TIMEOUT, true);
      case 429 -> new Classification(AwsFailureCategory.THROTTLING, true);
      case 500, 502, 503 -> new Classification(AwsFailureCategory.TRANSIENT_SERVICE, true);
      default ->
          status >= 400 && status < 500
              ? new Classification(AwsFailureCategory.INVALID_REQUEST, false)
              : new Classification(AwsFailureCategory.AUTOMATION, false);
    };
  }

  private static Integer statusCode(Throwable failure) {
    return switch (failure) {
      case SqsException value -> value.statusCode();
      case EventBridgeException value -> value.statusCode();
      default -> null;
    };
  }

  private static Throwable root(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null && current.getCause() != current)
      current = current.getCause();
    return current;
  }

  public record Classification(AwsFailureCategory category, boolean retryable) {}
}
