package com.codinglair.taf.runtime.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

final class FileSandbox {
  private final Path root;
  private final long maximumSize;

  FileSandbox(Path root, long maximumSize) {
    if (maximumSize < 1) throw new IllegalArgumentException("maximumSize must be positive");
    this.root = root.toAbsolutePath().normalize();
    this.maximumSize = maximumSize;
  }

  CheckedFile check(Path requested) {
    try {
      Path candidate = requested.isAbsolute() ? requested : root.resolve(requested);
      Path normalized = candidate.toAbsolutePath().normalize();
      if (!normalized.startsWith(root)) throw failure(FileValidationException.Kind.PATH_SAFETY);
      if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS))
        throw failure(FileValidationException.Kind.MISSING);
      Path real = normalized.toRealPath();
      if (!real.startsWith(root.toRealPath()))
        throw failure(FileValidationException.Kind.PATH_SAFETY);
      long size = Files.size(real);
      if (size > maximumSize) throw failure(FileValidationException.Kind.OVERSIZED);
      return new CheckedFile(real, size);
    } catch (FileValidationException failure) {
      throw failure;
    } catch (IOException failure) {
      throw new FileValidationException(
          FileValidationException.Kind.IO, "path check", "verify sandbox file access", null);
    }
  }

  InputStream open(CheckedFile file) {
    try {
      return new LimitedInputStream(Files.newInputStream(file.path()), maximumSize);
    } catch (IOException failure) {
      throw new FileValidationException(
          FileValidationException.Kind.IO, "open", "verify sandbox file access", null);
    }
  }

  long maximumSize() {
    return maximumSize;
  }

  private static FileValidationException failure(FileValidationException.Kind kind) {
    return new FileValidationException(
        kind,
        "path check",
        "use a regular file within the configured sandbox and size limit",
        null);
  }

  record CheckedFile(Path path, long size) {}

  private static final class LimitedInputStream extends InputStream {
    private final InputStream delegate;
    private long remaining;

    private LimitedInputStream(InputStream delegate, long maximumSize) {
      this.delegate = delegate;
      this.remaining = maximumSize;
    }

    @Override
    public int read() throws IOException {
      if (remaining == 0) return -1;
      int value = delegate.read();
      if (value >= 0) remaining--;
      return value;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
      if (remaining == 0) return -1;
      int count = delegate.read(target, offset, (int) Math.min(length, remaining));
      if (count > 0) remaining -= count;
      return count;
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }
}
