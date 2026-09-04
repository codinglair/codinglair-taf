package com.codinglair.taf.runtime.definition;

/** Allow-listed payload evidence; deliberately excludes location and bytes. */
public record PayloadEvidence(
    String logicalId, String checksum, String mediaType, long size, String version) {
  public static PayloadEvidence from(PayloadReference reference) {
    return new PayloadEvidence(
        reference.logicalId(),
        reference.checksum(),
        reference.mediaType(),
        reference.size(),
        reference.version());
  }
}
