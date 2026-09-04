package com.codinglair.taf.runtime.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class LocalSecretManagerTest {
  private static final String KEY_NAME = "TEST_MASTER_KEY";

  @Test
  void environmentProviderHandlesSuccessMissingBlankAndDenialWithoutLeaks() {
    Map<String, String> values = Map.of("USER_PASSWORD", "plain-canary");
    List<SecretAuditEvent> audit = new ArrayList<>();
    DefaultSecretManager manager =
        new DefaultSecretManager(List.of(new EnvironmentSecretProvider(values::get)), audit::add);
    try (ResolvedSecret secret = manager.resolve("secret://env/USER_PASSWORD", context(true))) {
      assertThat(secret.useAsString()).isEqualTo("plain-canary");
    }
    assertThat(audit.toString()).doesNotContain("plain-canary").doesNotContain("USER_PASSWORD");
    assertThatThrownBy(() -> manager.resolve("secret://env/USER_PASSWORD", context(false)))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageNotContaining("plain-canary");
    assertThatThrownBy(
            () ->
                new EnvironmentSecretProvider(name -> " ")
                    .resolve(SecretReference.parse("secret://env/USER_PASSWORD")))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageNotContaining("USER_PASSWORD");
  }

  @Test
  void jasyptRoundTripIsNondeterministicAndFailuresAreSecretSafe() {
    String key = UUID.randomUUID().toString();
    JasyptSecretProvider provider = new JasyptSecretProvider(name -> key, KEY_NAME);
    char[] plaintext = "jasypt-plain-canary".toCharArray();
    String first = provider.encryptForLocalDevelopment(plaintext);
    String second = provider.encryptForLocalDevelopment(plaintext);
    assertThat(first).isNotEqualTo(second);
    try (ResolvedSecret resolved =
        provider.resolve(SecretReference.parse("secret://jasypt/" + first))) {
      assertThat(resolved.useAsString()).isEqualTo(new String(plaintext));
    }
    String malformedCiphertext = "AAAAAAAAAAAAAAAA";
    assertThatThrownBy(
            () ->
                provider.resolve(
                    SecretReference.parse("secret://jasypt/" + malformedCiphertext)))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageNotContaining(first)
        .hasMessageNotContaining(key);
    assertThatThrownBy(() -> new JasyptSecretProvider(name -> null, KEY_NAME).verifyReady())
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageNotContaining(KEY_NAME);
  }

  @Test
  void concurrentPersonaResolutionDoesNotCrossContaminateOrCachePlaintext() throws Exception {
    Map<String, String> values =
        new ConcurrentHashMap<>(Map.of("USER_ONE", "one-canary", "USER_TWO", "two-canary"));
    DefaultSecretManager manager =
        new DefaultSecretManager(
            List.of(new EnvironmentSecretProvider(values::get)), SecretAuditSink.NO_OP);
    try (var executor = Executors.newFixedThreadPool(8)) {
      List<String> actual =
          executor
              .invokeAll(
                  java.util.stream.IntStream.range(0, 100)
                      .<java.util.concurrent.Callable<String>>mapToObj(
                          i ->
                              () -> {
                                String name = i % 2 == 0 ? "USER_ONE" : "USER_TWO";
                                try (ResolvedSecret secret =
                                    manager.resolve("secret://env/" + name, context(true))) {
                                  return secret.useAsString();
                                }
                              })
                      .toList())
              .stream()
              .map(
                  future -> {
                    try {
                      return future.get();
                    } catch (Exception failure) {
                      throw new IllegalStateException(failure);
                    }
                  })
              .toList();
      assertThat(actual).filteredOn("one-canary"::equals).hasSize(50);
      assertThat(actual).filteredOn("two-canary"::equals).hasSize(50);
    }
  }

  @Test
  void rejectsDuplicateProvidersDeterministically() {
    SecretProvider first = new EnvironmentSecretProvider(name -> "x");
    assertThatThrownBy(() -> new DefaultSecretManager(List.of(first, first), SecretAuditSink.NO_OP))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("env");
  }

  private static SecretRequestContext context(boolean authorized) {
    return new SecretRequestContext("test", "session", "local", authorized);
  }
}
