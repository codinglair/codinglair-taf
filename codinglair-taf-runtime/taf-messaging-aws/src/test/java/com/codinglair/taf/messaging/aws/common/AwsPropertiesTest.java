package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.messaging.aws.sqs.SqsControllerProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;

class AwsPropertiesTest {
  @Test
  void localstackRequiresEndpoint() {
    AwsConnectionProperties properties = new AwsConnectionProperties();
    properties.setRegion("us-east-1");
    properties.setEndpointMode(AwsEndpointMode.LOCALSTACK);
    assertThatThrownBy(() -> properties.validate("taf.aws.profiles.local"))
        .hasMessageContaining("endpoint-override")
        .hasMessageNotContaining("access");
  }

  @Test
  void nullValidationPathStillProducesAnActionableRegionFailure() {
    AwsConnectionProperties properties = new AwsConnectionProperties();

    assertThatThrownBy(() -> properties.validate(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("taf.aws.profiles.<profile>.region")
        .hasMessageContaining("is required");
  }

  @Test
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
  void plaintextCredentialIsRejectedWithoutEchoingIt() {
    AwsConnectionProperties properties = valid();
    properties.setCredentialProfileReference("plain-secret-value");
    assertThatThrownBy(() -> properties.validate("taf.aws.profiles.local"))
        .hasMessageContaining("Invalid secret reference")
        .hasMessageNotContaining("plain-secret-value");
  }

  @Test
  void externalResourcesCannotClaimDedicatedIsolation() {
    AwsConnectionProperties properties = valid();
    properties.setOwnershipMode(AwsOwnershipMode.EXTERNAL);
    SqsControllerProperties sqs = new SqsControllerProperties();
    sqs.setQueue("https://localhost/queue");
    properties.getSqs().put("orders", sqs);
    assertThatThrownBy(() -> properties.validate("taf.aws.profiles.local"))
        .hasMessageContaining("cannot claim dedicated ownership");
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
