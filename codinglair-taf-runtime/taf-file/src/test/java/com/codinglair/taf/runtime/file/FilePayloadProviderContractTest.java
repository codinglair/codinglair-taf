package com.codinglair.taf.runtime.file;

import com.codinglair.taf.runtime.definition.PayloadProviderContract;
import com.codinglair.taf.runtime.definition.PayloadReference;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

class FilePayloadProviderContractTest extends PayloadProviderContract {
  @TempDir Path root;

  @Override
  protected Fixture fixture(byte[] bytes, String mediaType) throws Exception {
    Path file = Files.write(root.resolve("contract.pdf"), bytes);
    return new Fixture(
        new FilePayloadResolver(root, 1024),
        new PayloadReference(
            "contract", file.toUri(), checksum(bytes), mediaType, bytes.length, "v1"),
        () -> {});
  }
}
