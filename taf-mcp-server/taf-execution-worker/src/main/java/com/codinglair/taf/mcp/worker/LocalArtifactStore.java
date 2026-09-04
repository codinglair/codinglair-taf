package com.codinglair.taf.mcp.worker;

import com.codinglair.taf.mcp.jobs.JobId;
import com.codinglair.taf.mcp.jobs.JobReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Local controlled artifact provider; its root is independent from execution workspaces. */
public final class LocalArtifactStore implements ArtifactStore {
  private final Path root;

  public LocalArtifactStore(Path root) throws IOException {
    this.root = root.toAbsolutePath().normalize();
    Files.createDirectories(this.root);
    if (Files.isSymbolicLink(this.root)) {
      throw new IllegalArgumentException("Artifact root cannot be a symbolic link");
    }
  }

  @Override
  public JobReference store(JobId jobId, String relativePath, Path source) throws IOException {
    var jobRoot = root.resolve(WorkerPathNames.jobDirectory(jobId.value())).normalize();
    var target = jobRoot.resolve(relativePath).normalize();
    if (!target.startsWith(jobRoot)
        || Files.isSymbolicLink(source)
        || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("Artifact path escapes its controlled root");
    }
    Files.createDirectories(target.getParent());
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    return new JobReference(
        "taf://artifact/" + jobId.value() + "/" + relativePath.replace('\\', '/'),
        "application/octet-stream");
  }
}
