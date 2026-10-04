package com.codinglair.taf.runtime.secret.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.secret.ResolvedSecret;
import com.codinglair.taf.runtime.secret.SecretProvider;
import com.codinglair.taf.runtime.secret.SecretReference;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Secret preflight contributor")
class SecretPreflightContributorTest {
  @Test
  @DisplayName("verifies provider readiness once for duplicate provider references")
  void verifiesProviderReadinessOnceForDuplicateProviderReferences() {
    SecretProperties properties = new SecretProperties();
    properties.getReferences().add("secret://env/FIRST_SECRET");
    properties.getReferences().add("secret://env/SECOND_SECRET");
    AtomicInteger readinessChecks = new AtomicInteger();
    SecretProvider provider = provider(readinessChecks);

    assertThat(new SecretPreflightContributor(properties, List.of(provider)).inspect()).isEmpty();
    assertThat(readinessChecks).hasValue(1);
  }

  private static SecretProvider provider(AtomicInteger readinessChecks) {
    return new SecretProvider() {
      @Override
      public String id() {
        return "env";
      }

      @Override
      public void verifyReady() {
        readinessChecks.incrementAndGet();
      }

      @Override
      public ResolvedSecret resolve(SecretReference reference) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
