package com.codinglair.taf.mcp.prompts;

import java.util.List;

/** Location-independent source for controlled reports and evidence. */
@FunctionalInterface
public interface ReportRepository {
  List<StoredResource> findByJobId(String jobId);
}
