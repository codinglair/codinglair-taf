package com.codinglair.taf.runtime.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Reusable contract for external binary payload providers. */
public abstract class PayloadProviderContract {
  protected abstract Fixture fixture(byte[] bytes, String mediaType) throws Exception;

  @Test
  void streamsUnchangedAndSupportsRanges() throws Exception {
    byte[] bytes = "%PDF-contract-content".getBytes();
    try (Fixture fixture = fixture(bytes, "application/pdf")) {
      try (ResolvedPayload payload = fixture.resolver().open(fixture.reference())) {
        assertThat(payload.stream().readAllBytes()).isEqualTo(bytes);
        assertThat(payload.evidence()).isEqualTo(PayloadEvidence.from(fixture.reference()));
      }
      try (ResolvedPayload payload =
          fixture.resolver().open(fixture.reference(), new PayloadRange(1, 4))) {
        assertThat(payload.stream().readAllBytes()).containsExactly('P', 'D', 'F', '-');
      }
    }
  }

  @Test
  void rejectsChecksumMismatchWithoutLeakingLocation() throws Exception {
    byte[] bytes = "%PDF-contract-content".getBytes();
    try (Fixture fixture = fixture(bytes, "application/pdf")) {
      PayloadReference original = fixture.reference();
      PayloadReference invalid =
          new PayloadReference(
              original.logicalId(),
              original.location(),
              checksum("different".getBytes()),
              original.mediaType(),
              original.size(),
              original.version());
      assertThatThrownBy(() -> fixture.resolver().open(invalid))
          .isInstanceOfSatisfying(
              PayloadException.class,
              failure -> {
                assertThat(failure.kind()).isEqualTo(PayloadException.Kind.CHECKSUM);
                assertThat(failure.getMessage()).doesNotContain(original.location().toString());
              });
    }
  }

  protected static String checksum(byte[] bytes) throws Exception {
    return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  protected record Fixture(
      PayloadResolver resolver, PayloadReference reference, AutoCloseable cleanup)
      implements AutoCloseable {
    public Fixture {
      if (reference.location().equals(URI.create("about:blank"))) {
        throw new IllegalArgumentException("fixture must use a real provider location");
      }
    }

    @Override
    public void close() throws Exception {
      cleanup.close();
    }
  }
}
