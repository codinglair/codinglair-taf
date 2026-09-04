package com.codinglair.taf.mcp.worker;

import com.codinglair.taf.mcp.jobs.JobId;
import com.codinglair.taf.mcp.jobs.JobReference;
import java.io.IOException;
import java.nio.file.Path;

public interface ArtifactStore {
  JobReference store(JobId jobId, String relativePath, Path source) throws IOException;
}
