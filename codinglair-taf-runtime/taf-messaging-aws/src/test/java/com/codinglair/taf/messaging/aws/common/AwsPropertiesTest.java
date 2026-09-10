package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.messaging.aws.sqs.SqsControllerProperties;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AWS configuration properties")
class AwsPropertiesTest {
  @Test
  @DisplayName("allows a managed LocalStack endpoint to be acquired dynamically")
  void managedLocalstackMayAcquireItsEndpointDynamically() {
    AwsConnectionProperties properties = new AwsConnectionProperties();
    properties.setRegion("us-east-1");
    properties.setEndpointMode(AwsEndpointMode.LOCALSTACK);
    properties.setOwnershipMode(AwsOwnershipMode.TEST_OWNED);
    assertThatCode(() -> properties.validate("taf.aws.profiles.local")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("uses an actionable default path during direct validation")
  void nullValidationPathStillProducesAnActionableRegionFailure() {
    AwsConnectionProperties properties = new AwsConnectionProperties();

    assertThatThrownBy(() -> properties.validate(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("taf.aws.profiles.<profile>.region")
        .hasMessageContaining("is required");
  }

  @Test
  @DisplayName("reports empty profiles as a structured configuration failure")
  void emptyProfilesProduceStructuredConfigurationFailure() {
    AwsProperties properties = new AwsProperties();

    assertThatThrownBy(properties::validate)
        .isInstanceOfSatisfying(
            AwsConfigurationException.class,
            failure -> {
              assertThat(failure.capability()).isEqualTo("AWS messaging");
              assertThat(failure.path()).isEqualTo("taf.aws.profiles");
              assertThat(failure.correctiveAction())
                  .isEqualTo("at least one named profile is required");
            });
  }

  @Test
  @DisplayName("rejects plaintext credentials without echoing them")
  void plaintextCredentialIsRejectedWithoutEchoingIt() {
    AwsConnectionProperties properties = valid();
    properties.setCredentialProfileReference("plain-secret-value");
    assertThatThrownBy(() -> properties.validate("taf.aws.profiles.local"))
        .hasMessageContaining("Invalid secret reference")
        .hasMessageNotContaining("plain-secret-value");
  }

  @Test
  @DisplayName("prevents external resources from claiming dedicated isolation")
  void externalResourcesCannotClaimDedicatedIsolation() {
    AwsConnectionProperties properties = valid();
    properties.setOwnershipMode(AwsOwnershipMode.EXTERNAL);
    SqsControllerProperties sqs = new SqsControllerProperties();
    sqs.setQueue("https://localhost/queue");
    properties.getSqs().put("orders", sqs);
    assertThatThrownBy(() -> properties.validate("taf.aws.profiles.local"))
        .hasMessageContaining("cannot claim dedicated ownership");
  }

  @Test
  @DisplayName("rejects unsafe shared queue scanning with corrective isolation guidance")
  void unsafeSharedQueueFailsPreflight() {
    AwsConnectionProperties properties = valid();
    properties.setOwnershipMode(AwsOwnershipMode.EXTERNAL);
    SqsControllerProperties sqs = new SqsControllerProperties();
    sqs.setQueue("https://localhost/queue");
    sqs.setIsolationMode(SqsIsolationMode.EXTERNAL_SHARED);
    properties.getSqs().put("orders", sqs);

    assertThatThrownBy(() -> properties.validate("taf.aws.profiles.local"))
        .hasMessageContaining("is unsafe")
        .hasMessageContaining("CONTROLLED_CONSUMER")
        .hasMessageContaining("MIRROR_QUEUE");
  }

  @Test
  @DisplayName("rejects an administrative receive batch bound above the SQS limit")
  void receiveBatchBoundIsValidated() {
    AwsConnectionProperties properties = valid();
    properties.getPolicy().setMaximumReceiveMessages(11);

    assertThatThrownBy(() -> properties.validate("taf.aws.profiles.local"))
        .hasMessageContaining("maximum-receive-messages")
        .hasMessageContaining("between 1 and 10");
  }

  @Test
  @DisplayName("rejects endpoint user-info without disclosing it")
  void endpointUserInfoIsRejectedWithoutDisclosure() {
    AwsConnectionProperties properties = valid();
    properties.setEndpointOverride(URI.create("http://canary-secret@localhost:4566"));

    assertThatThrownBy(() -> properties.validate("taf.aws.profiles.local"))
        .hasMessageContaining("must not contain user-info")
        .hasMessageNotContaining("canary-secret");
  }

  private static AwsConnectionProperties valid() {
    AwsConnectionProperties properties = new AwsConnectionProperties();
    properties.setRegion("us-east-1");
    properties.setEndpointMode(AwsEndpointMode.LOCALSTACK);
    properties.setEndpointOverride(URI.create("http://localhost:4566"));
    properties.setOwnershipMode(AwsOwnershipMode.TEST_OWNED);
    return properties;
  }
}
