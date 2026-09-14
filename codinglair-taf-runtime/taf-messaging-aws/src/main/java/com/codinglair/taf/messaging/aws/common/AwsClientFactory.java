package com.codinglair.taf.messaging.aws.common;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Internal AWS SDK client factory used by the controller implementations. This package-private type
 * is not part of the stable framework API.
 */
final class AwsClientFactory {
  SqsClient sqs(AwsConnectionProperties properties) {
    var builder =
        SqsClient.builder()
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(credentials(properties));
    if (properties.getEndpointOverride() != null)
      builder.endpointOverride(properties.getEndpointOverride());
    return builder.build();
  }

  EventBridgeClient eventbridge(AwsConnectionProperties properties) {
    var builder =
        EventBridgeClient.builder()
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(credentials(properties));
    if (properties.getEndpointOverride() != null)
      builder.endpointOverride(properties.getEndpointOverride());
    return builder.build();
  }

  private static AwsCredentialsProvider credentials(AwsConnectionProperties properties) {
    String reference = properties.getCredentialProfileReference();
    if (reference != null)
      return verified(
          ProfileCredentialsProvider.builder()
              .profileName(reference.substring("credential://".length()))
              .build());
    if (properties.getEndpointMode() == AwsEndpointMode.LOCALSTACK)
      // LocalStack accepts non-secret placeholder credentials. This branch is never used for AWS.
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create("localstack", "localstack"));
    return verified(DefaultCredentialsProvider.builder().build());
  }

  private static AwsCredentialsProvider verified(AwsCredentialsProvider provider) {
    // Resolve at the deterministic client-construction boundary so unavailable credentials fail
    // before an operation. The resolved value is deliberately neither retained nor diagnosed.
    provider.resolveCredentials();
    return provider;
  }
}
