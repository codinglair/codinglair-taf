package com.codinglair.taf.runtime.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.definition.PayloadException;
import com.codinglair.taf.runtime.definition.PayloadRange;
import com.codinglair.taf.runtime.definition.PayloadReference;
import com.codinglair.taf.runtime.definition.ResolvedPayload;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilePayloadResolverTest {
  @TempDir Path root;

  @Test
  void streamsPdfUnchangedAndOnlyAllowListedEvidence() throws Exception {
    byte[] bytes = "%PDF-1.7\nunchanged".getBytes();
    Path file = Files.write(root.resolve("sample.pdf"), bytes);
    PayloadReference reference = reference("pdf", file.toUri(), bytes, "application/pdf");
    try (ResolvedPayload payload = new FilePayloadResolver(root, 1024).open(reference)) {
      assertThat(payload.stream().readAllBytes()).isEqualTo(bytes);
      assertThat(payload.evidence().toString()).doesNotContain(file.toString(), "%PDF");
    }
  }

  @Test
  void streamsImageRangeAndClosesIdempotently() throws Exception {
    byte[] bytes = new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
    Path file = Files.write(root.resolve("sample.png"), bytes);
    PayloadReference reference = reference("image", file.toUri(), bytes, "image/png");
    ResolvedPayload payload =
        new FilePayloadResolver(root, 1024).open(reference, new PayloadRange(2, 5));
    assertThat(payload.stream().readAllBytes()).containsExactly('N', 'G', 1, 2);
    payload.close();
    payload.close();
  }

  @Test
  void rejectsMissingTraversalMutationOversizeMediaTypeAndInvalidRange() throws Exception {
    FilePayloadResolver resolver = new FilePayloadResolver(root, 4);
    byte[] bytes = "value".getBytes();
    Path file = Files.write(root.resolve("value.pdf"), bytes);
    assertKind(
        resolver,
        reference("missing", root.resolve("gone.pdf").toUri(), bytes, "application/pdf"),
        PayloadException.Kind.MISSING);
    assertKind(
        resolver,
        reference("traversal", URI.create("git:../outside.pdf"), bytes, "application/pdf"),
        PayloadException.Kind.PATH_SAFETY);
    assertKind(
        resolver,
        reference("large", file.toUri(), bytes, "application/pdf"),
        PayloadException.Kind.OVERSIZED);
    resolver = new FilePayloadResolver(root, 1024);
    assertKind(
        resolver,
        reference("media", file.toUri(), bytes, "image/png"),
        PayloadException.Kind.MEDIA_TYPE);
    PayloadReference mutated =
        reference("mutated", file.toUri(), "other".getBytes(), "application/pdf");
    assertKind(resolver, mutated, PayloadException.Kind.CHECKSUM);
    FilePayloadResolver finalResolver = resolver;
    PayloadReference valid = reference("range", file.toUri(), bytes, "application/pdf");
    assertThatThrownBy(() -> finalResolver.open(valid, new PayloadRange(0, 5)))
        .isInstanceOf(PayloadException.class)
        .extracting("kind")
        .isEqualTo(PayloadException.Kind.RANGE);
  }

  @Test
  void supportsConcurrentIndependentReaders() throws Exception {
    byte[] bytes = "%PDF-concurrent".getBytes();
    Path file = Files.write(root.resolve("concurrent.pdf"), bytes);
    PayloadReference reference = reference("concurrent", file.toUri(), bytes, "application/pdf");
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks =
          java.util.stream.IntStream.range(0, 20)
              .mapToObj(
                  _ ->
                      (java.util.concurrent.Callable<byte[]>)
                          () -> {
                            try (var payload =
                                new FilePayloadResolver(root, 1024).open(reference)) {
                              return payload.stream().readAllBytes();
                            }
                          })
              .toList();
      for (var result : executor.invokeAll(tasks)) assertThat(result.get()).isEqualTo(bytes);
    }
  }

  private static void assertKind(
      FilePayloadResolver resolver, PayloadReference reference, PayloadException.Kind kind) {
    assertThatThrownBy(() -> resolver.open(reference))
        .isInstanceOf(PayloadException.class)
        .extracting("kind")
        .isEqualTo(kind)
        .asString()
        .doesNotContain(reference.location().toString());
  }

  private static PayloadReference reference(String id, URI location, byte[] bytes, String mediaType)
      throws Exception {
    String checksum =
        "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    return new PayloadReference(id, location, checksum, mediaType, bytes.length, "v1");
  }
}
