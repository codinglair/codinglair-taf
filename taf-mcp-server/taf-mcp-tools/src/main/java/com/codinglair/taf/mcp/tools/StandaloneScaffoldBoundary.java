package com.codinglair.taf.mcp.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Canonical filesystem boundary shared by every official scaffold write and revert path. */
final class StandaloneScaffoldBoundary {
  private static final Path DESCRIPTOR = Path.of("src/test/resources/taf-project.json");
  private final Path approvedRoot;

  StandaloneScaffoldBoundary(Path approvedRoot) {
    this.approvedRoot = realExisting(approvedRoot, "approved root");
  }

  Path resolve(Path automationRoot, Path sutRoot) {
    return resolve(automationRoot, sutRoot, true);
  }

  Path resolveLegacy(Path automationRoot, Path sutRoot) {
    return resolve(automationRoot, sutRoot, false);
  }

  private Path resolve(Path automationRoot, Path sutRoot, boolean requireRecognizedProject) {
    Path candidate = absolute(automationRoot);
    Path existing = nearestExisting(candidate);
    Path canonical =
        realExisting(existing, "automation root ancestor")
            .resolve(existing.relativize(candidate))
            .normalize();
    if (!canonical.equals(candidate))
      throw new IllegalArgumentException("filesystem-link automation ancestor is not allowed");
    if (!canonical.startsWith(approvedRoot))
      throw new IllegalArgumentException("automation root escapes the approved root");
    if (sutRoot != null && canonical.equals(canonicalPotential(sutRoot)))
      throw new IllegalArgumentException("automation root must not be the SUT build root");
    rejectLinks(existing, candidate);
    if (requireRecognizedProject) recognize(canonical);
    return canonical;
  }

  Path target(Path root, Path relative) {
    if (relative.isAbsolute()) throw new IllegalArgumentException("scaffold path must be relative");
    Path target = root.resolve(relative).normalize();
    if (!target.startsWith(root))
      throw new IllegalArgumentException("scaffold path escapes automation root");
    Path existing = nearestExisting(target);
    Path resolved =
        realExisting(existing, "scaffold target ancestor")
            .resolve(existing.relativize(target))
            .normalize();
    if (!resolved.equals(target.toAbsolutePath().normalize()))
      throw new IllegalArgumentException("filesystem-link scaffold ancestor is not allowed");
    if (!resolved.startsWith(root))
      throw new IllegalArgumentException("scaffold target escapes automation root through a link");
    rejectLinks(root, target);
    return target;
  }

  void verifyTargetAtWrite(Path root, Path target) {
    target(root, root.relativize(target));
  }

  void verifyRootAtWrite(Path root, Path sutRoot) {
    Path resolved = resolve(root, sutRoot, false);
    if (!resolved.equals(root.toAbsolutePath().normalize()))
      throw new IllegalArgumentException("automation root was substituted during publication");
  }

  private Path absolute(Path path) {
    Path normalized = path.isAbsolute() ? path.normalize() : approvedRoot.resolve(path).normalize();
    return normalized.toAbsolutePath().normalize();
  }

  private Path canonicalPotential(Path path) {
    Path candidate = absolute(path);
    Path existing = nearestExisting(candidate);
    return realExisting(existing, "SUT root ancestor")
        .resolve(existing.relativize(candidate))
        .normalize();
  }

  private static void recognize(Path root) {
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
      throw new IllegalArgumentException("automation root must be a real directory");
    try (var entries = Files.list(root)) {
      if (entries.findAny().isPresent() && !Files.isRegularFile(root.resolve(DESCRIPTOR)))
        throw new IllegalArgumentException(
            "automation root is nonempty and is not a recognized TAF consumer project");
    } catch (IOException failure) {
      throw new IllegalArgumentException("automation root cannot be inspected", failure);
    }
  }

  private static void rejectLinks(Path from, Path to) {
    if (!to.startsWith(from)) return;
    Path current = from;
    for (Path part : from.relativize(to)) {
      current = current.resolve(part);
      if (Files.isSymbolicLink(current))
        throw new IllegalArgumentException("symbolic-link scaffold path is not allowed");
    }
  }

  private static Path nearestExisting(Path path) {
    Path current = path;
    while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS))
      current = current.getParent();
    if (current == null) throw new IllegalArgumentException("path has no existing ancestor");
    return current;
  }

  private static Path realExisting(Path path, String label) {
    try {
      return path.toRealPath();
    } catch (IOException failure) {
      throw new IllegalArgumentException(label + " is unavailable", failure);
    }
  }
}
