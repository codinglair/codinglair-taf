package com.codinglair.taf.mcp.resources;

import java.util.Objects;

/** Repository entry separating non-returned access scope from safe resource metadata. */
public record StoredEvidenceMetadata(EvidenceAccessScope access, EvidenceMetadata metadata) {
  public StoredEvidenceMetadata {
    Objects.requireNonNull(access, "access");
    Objects.requireNonNull(metadata, "metadata");
  }
}
