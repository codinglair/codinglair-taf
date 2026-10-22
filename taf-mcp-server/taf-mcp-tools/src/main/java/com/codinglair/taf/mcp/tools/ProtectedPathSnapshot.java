package com.codinglair.taf.mcp.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Immutable hashes for the explicitly declared SUT scope, excluding its automation subtree. */
record ProtectedPathSnapshot(Path root, Map<Path, String> hashes) {
  ProtectedPathSnapshot {
    hashes = Map.copyOf(hashes);
  }

  static ProtectedPathSnapshot capture(Path sutRoot, Path automationRoot) {
    if (sutRoot == null) return new ProtectedPathSnapshot(null, Map.of());
    try {
      Path root = sutRoot.toRealPath();
      Path excluded = automationRoot.toAbsolutePath().normalize();
      var hashes = new TreeMap<Path, String>();
      try (var paths = Files.walk(root)) {
        paths
            .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            .filter(path -> !path.toAbsolutePath().normalize().startsWith(excluded))
            .forEach(path -> hashes.put(root.relativize(path), hash(path)));
      }
      return new ProtectedPathSnapshot(root, hashes);
    } catch (IOException failure) {
      throw new IllegalArgumentException("protected SUT scope cannot be snapshotted", failure);
    }
  }

  void verifyUnchanged(Path automationRoot) {
    if (root == null) return;
    var after = capture(root, automationRoot);
    if (!hashes.equals(after.hashes))
      throw new IllegalStateException("protected SUT files changed during scaffold publication");
  }

  private static String hash(Path path) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    } catch (IOException failure) {
      throw new IllegalArgumentException("protected file cannot be read: " + path, failure);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
