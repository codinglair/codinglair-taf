package com.codinglair.taf.contracts;

/** A versioned, relative-path scaffold asset. */
public record GeneratedContractAsset(String relativePath, String assetVersion, String content) {
  public GeneratedContractAsset {
    if (relativePath == null
        || relativePath.isBlank()
        || relativePath.startsWith("/")
        || relativePath.contains("..")) {
      throw new IllegalArgumentException("relativePath must be a confined relative path");
    }
    if (assetVersion == null || assetVersion.isBlank())
      throw new IllegalArgumentException("assetVersion must not be blank");
    if (content == null || content.isBlank())
      throw new IllegalArgumentException("content must not be blank");
  }
}
