package com.codinglair.taf.mcp.worker;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

final class WorkspacePreparer {
  private final Path executionRoot;
  private final WorkerLimits limits;

  WorkspacePreparer(Path executionRoot, WorkerLimits limits) {
    this.executionRoot = executionRoot.toAbsolutePath().normalize();
    this.limits = limits;
  }

  Path prepare(WorkerRequest request) throws IOException {
    var source = request.sourceWorkspace();
    if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
      throw new WorkerExecutionException("INVALID_WORKSPACE", "Workspace must be a real directory");
    }
    Files.createDirectories(executionRoot);
    if (Files.isSymbolicLink(executionRoot)) {
      throw new WorkerExecutionException("INVALID_WORKSPACE", "Execution root cannot be a link");
    }
    var target =
        executionRoot.resolve(WorkerPathNames.jobDirectory(request.jobId().value())).normalize();
    if (!target.startsWith(executionRoot) || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
      throw new WorkerExecutionException(
          "INVALID_WORKSPACE", "Execution workspace is not available");
    }
    var count = new long[] {0};
    var bytes = new long[] {0};
    Files.walkFileTree(
        source,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
              throws IOException {
            rejectLink(directory, attributes);
            var destination = resolve(source, target, directory);
            Files.createDirectories(destination);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
              throws IOException {
            rejectLink(file, attributes);
            if (!attributes.isRegularFile()) {
              throw new WorkerExecutionException(
                  "INVALID_WORKSPACE", "Workspace contains a special file");
            }
            count[0]++;
            bytes[0] += attributes.size();
            if (count[0] > limits.maximumWorkspaceFiles()
                || bytes[0] > limits.maximumWorkspaceBytes()) {
              throw new WorkerExecutionException(
                  "WORKSPACE_LIMIT", "Workspace exceeds administrative limits");
            }
            Files.copy(file, resolve(source, target, file));
            return FileVisitResult.CONTINUE;
          }
        });
    return target;
  }

  static Path contained(Path root, String relative) {
    var candidate = Path.of(relative);
    if (candidate.isAbsolute()) {
      throw new WorkerExecutionException("PATH_ESCAPE", "Only relative paths are allowed");
    }
    var resolved = root.resolve(candidate).normalize();
    if (!resolved.startsWith(root)) {
      throw new WorkerExecutionException("PATH_ESCAPE", "Path escapes the worker workspace");
    }
    return resolved;
  }

  static void delete(Path root) throws IOException {
    if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path directory, IOException error)
              throws IOException {
            if (error != null) {
              throw error;
            }
            Files.delete(directory);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  private static Path resolve(Path source, Path target, Path entry) {
    var resolved = target.resolve(source.relativize(entry)).normalize();
    if (!resolved.startsWith(target)) {
      throw new WorkerExecutionException("PATH_ESCAPE", "Workspace entry escapes target");
    }
    return resolved;
  }

  private static void rejectLink(Path path, BasicFileAttributes attributes) {
    if (attributes.isSymbolicLink() || Files.isSymbolicLink(path)) {
      throw new WorkerExecutionException("PATH_ESCAPE", "Workspace links are not allowed");
    }
  }
}
