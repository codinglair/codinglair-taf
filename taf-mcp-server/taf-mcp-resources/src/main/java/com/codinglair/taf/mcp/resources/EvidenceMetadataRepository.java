package com.codinglair.taf.mcp.resources;

import java.util.List;

/** Location-independent evidence metadata source. */
@FunctionalInterface
public interface EvidenceMetadataRepository {
  List<StoredEvidenceMetadata> findByJobId(String jobId);
}
