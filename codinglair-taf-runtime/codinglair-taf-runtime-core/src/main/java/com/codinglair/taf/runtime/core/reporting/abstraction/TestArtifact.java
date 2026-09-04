package com.codinglair.taf.runtime.core.reporting.abstraction;

import java.security.MessageDigest;
import java.util.Base64;

/** Represents an artifact attached to a test execution. */
public record TestArtifact(
    String name, String type, String content, String contentType, String stepName, String hash) {

  /**
   * Create an artifact without hash or step association.
   *
   * @param name the artifact name
   * @param type the artifact type
   * @param content the artifact content
   * @return the artifact
   */
  public static TestArtifact of(String name, String type, String content) {
    return new TestArtifact(name, type, content, null, null, null);
  }

  /**
   * Create an artifact with explicit content type.
   *
   * @param name the artifact name
   * @param type the artifact type
   * @param content the artifact content
   * @param contentType the content type
   * @return the artifact
   */
  public static TestArtifact of(String name, String type, String content, String contentType) {
    return new TestArtifact(name, type, content, contentType, null, null);
  }

  /**
   * Create an artifact with hash and step association.
   *
   * @param name the artifact name
   * @param type the artifact type
   * @param content the artifact content
   * @param contentType the content type
   * @param stepName the associated step name
   * @param hash the SHA-256 hash
   * @return the artifact
   */
  public static TestArtifact of(
      String name, String type, String content, String contentType, String stepName, String hash) {
    return new TestArtifact(name, type, content, contentType, stepName, hash);
  }

  /**
   * Compute SHA-256 hash of the content.
   *
   * @param content the content to hash
   * @return the Base64-encoded SHA-256 hash
   */
  public static String computeHash(String content) {
    if (content == null) {
      return null;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(content.getBytes());
      return Base64.getEncoder().encodeToString(hashBytes);
    } catch (Exception e) {
      throw new RuntimeException("Failed to compute SHA-256 hash", e);
    }
  }
}
