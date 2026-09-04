package com.codinglair.taf.runtime.file;

import com.codinglair.taf.runtime.definition.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** Resolves file and Git-worktree payload references beneath one configured root. */
public final class FilePayloadResolver implements PayloadResolver {
  private final Path root;
  private final long maximumSize;

  public FilePayloadResolver(Path root, long maximumSize) {
    this.root = root.toAbsolutePath().normalize();
    if (maximumSize < 0) throw new IllegalArgumentException("maximumSize must not be negative");
    this.maximumSize = maximumSize;
  }

  @Override
  public ResolvedPayload open(PayloadReference reference) {
    if (reference.size() == 0) return openEmpty(reference);
    return open(reference, PayloadRange.all(reference.size()));
  }

  @Override
  public ResolvedPayload open(PayloadReference reference, PayloadRange range) {
    Path path = resolve(reference);
    validate(reference, path);
    if (range.endInclusive() >= reference.size())
      throw failure(PayloadException.Kind.RANGE, reference);
    try {
      InputStream input = Files.newInputStream(path);
      input.skipNBytes(range.start());
      return new ResolvedPayload(
          PayloadEvidence.from(reference),
          range,
          new BoundedInputStream(input, range.length()),
          () -> {});
    } catch (IOException _) {
      throw failure(PayloadException.Kind.IO, reference);
    }
  }

  private ResolvedPayload openEmpty(PayloadReference reference) {
    Path path = resolve(reference);
    validate(reference, path);
    return new ResolvedPayload(
        PayloadEvidence.from(reference),
        PayloadRange.empty(),
        InputStream.nullInputStream(),
        () -> {});
  }

  private Path resolve(PayloadReference reference) {
    String scheme = reference.location().getScheme();
    if (scheme != null && !scheme.equals("file") && !scheme.equals("git"))
      throw failure(PayloadException.Kind.PATH_SAFETY, reference);
    String raw =
        scheme == null
            ? reference.location().getPath()
            : scheme.equals("git") ? reference.location().getSchemeSpecificPart() : null;
    Path candidate =
        scheme != null && scheme.equals("file") ? Path.of(reference.location()) : root.resolve(raw);
    Path normalized = candidate.toAbsolutePath().normalize();
    if (!normalized.startsWith(root)) throw failure(PayloadException.Kind.PATH_SAFETY, reference);
    return normalized;
  }

  private void validate(PayloadReference reference, Path path) {
    try {
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        throw failure(PayloadException.Kind.MISSING, reference);
      Path real = path.toRealPath();
      if (!real.startsWith(root.toRealPath()))
        throw failure(PayloadException.Kind.PATH_SAFETY, reference);
      long size = Files.size(real);
      if (size > maximumSize || size > reference.size())
        throw failure(PayloadException.Kind.OVERSIZED, reference);
      if (size != reference.size()) throw failure(PayloadException.Kind.CHECKSUM, reference);
      String detected = Files.probeContentType(real);
      if (detected == null || !detected.equalsIgnoreCase(reference.mediaType()))
        throw failure(PayloadException.Kind.MEDIA_TYPE, reference);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream input = Files.newInputStream(real);
          DigestInputStream checked = new DigestInputStream(input, digest)) {
        checked.transferTo(OutputStream.nullOutputStream());
      }
      String actual = "sha256:" + HexFormat.of().formatHex(digest.digest());
      if (!MessageDigest.isEqual(actual.getBytes(), reference.checksum().getBytes()))
        throw failure(PayloadException.Kind.CHECKSUM, reference);
    } catch (PayloadException e) {
      throw e;
    } catch (IOException _) {
      throw failure(PayloadException.Kind.IO, reference);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static PayloadException failure(PayloadException.Kind kind, PayloadReference reference) {
    return new PayloadException(
        kind,
        reference.logicalId(),
        "payload "
            + reference.logicalId()
            + " failed "
            + kind.name().toLowerCase()
            + " validation");
  }

  private static final class BoundedInputStream extends FilterInputStream {
    private long remaining;

    BoundedInputStream(InputStream input, long remaining) {
      super(input);
      this.remaining = remaining;
    }

    @Override
    public int read() throws IOException {
      if (remaining == 0) return -1;
      int value = super.read();
      if (value >= 0) remaining--;
      return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      if (remaining == 0) return -1;
      int read = super.read(bytes, offset, (int) Math.min(length, remaining));
      if (read > 0) remaining -= read;
      return read;
    }
  }
}
