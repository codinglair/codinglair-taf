package com.codinglair.taf.mcp.tools;

import com.codinglair.taf.mcp.security.ApprovalRequest;
import com.codinglair.taf.mcp.security.ApprovalService;
import com.codinglair.taf.mcp.security.AuthorizationContext;
import com.codinglair.taf.mcp.security.EnforcementRequest;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Applies create-only proposed changes and can remove only unchanged files from its provenance. */
public final class ScaffoldWorkflow {
  private static final String DIGEST_SEPARATOR = "\u001f";
  private final Path approvedRoot;
  private final ScaffoldTemplateRegistry templates;
  private final McpEnforcementService enforcement;
  private final ApprovalService approvals;
  private final ScaffoldCompiler compiler;
  private final Clock clock;

  public ScaffoldWorkflow(
      Path approvedRoot,
      ScaffoldTemplateRegistry templates,
      McpEnforcementService enforcement,
      ApprovalService approvals,
      ScaffoldCompiler compiler,
      Clock clock) {
    this.approvedRoot = approvedRoot.toAbsolutePath().normalize();
    this.templates = Objects.requireNonNull(templates, "templates");
    this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
    this.approvals = Objects.requireNonNull(approvals, "approvals");
    this.compiler = Objects.requireNonNull(compiler, "compiler");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public ScaffoldResult apply(ScaffoldRequest request) {
    Objects.requireNonNull(request, "request");
    var template = templates.require(request.template(), request.templateVersion());
    var destination = confinedDestination(request);
    var digest = digest(request, template);
    var context = context(request, "scaffold", "taf:working-tree:write");
    if (template.assets().stream()
        .anyMatch(asset -> asset.kind() == ScaffoldAsset.Kind.DEPENDENCY)) {
      var dependencyContext = context(request, "dependency.add", "taf:dependency:write");
      if (request.dependencyApprovalId() == null
          || !approvals.permits(request.dependencyApprovalId(), dependencyContext, digest)) {
        return result(ScaffoldResult.Status.APPROVAL_REQUIRED, "dependency approval required");
      }
    }
    var enforced =
        enforcement.enforce(
            new EnforcementRequest(
                request.requestId(),
                request.transport(),
                context,
                digest,
                request.writeApprovalId(),
                Map.of("template", template.name(), "version", template.version())),
            () -> applyApproved(destination, template));
    return switch (enforced.status()) {
      case ALLOWED -> (ScaffoldResult) enforced.body();
      case APPROVAL_REQUIRED ->
          result(ScaffoldResult.Status.APPROVAL_REQUIRED, "write approval required");
      case DENIED -> result(ScaffoldResult.Status.DENIED, "authorization denied");
      case INPUT_REJECTED, FAILED -> result(ScaffoldResult.Status.FAILED, "scaffold failed safely");
    };
  }

  public ApprovalRequest requestWriteApproval(ScaffoldRequest request, Duration ttl) {
    var template = templates.require(request.template(), request.templateVersion());
    confinedDestination(request);
    return approvals.request(
        context(request, "scaffold", "taf:working-tree:write"), digest(request, template), ttl);
  }

  public ApprovalRequest requestDependencyApproval(ScaffoldRequest request, Duration ttl) {
    var template = templates.require(request.template(), request.templateVersion());
    confinedDestination(request);
    if (template.assets().stream()
        .noneMatch(asset -> asset.kind() == ScaffoldAsset.Kind.DEPENDENCY)) {
      throw new IllegalArgumentException("template does not add dependencies");
    }
    return approvals.request(
        context(request, "dependency.add", "taf:dependency:write"), digest(request, template), ttl);
  }

  public ScaffoldResult revert(ScaffoldRequest request, ScaffoldProvenance provenance) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(provenance, "provenance");
    var template = templates.require(request.template(), request.templateVersion());
    var destination = confinedDestination(request);
    if (!matchesTemplate(template, provenance)) {
      return result(ScaffoldResult.Status.CONFLICT, "scaffold provenance does not match template");
    }
    var operationDigest = digest(request, template);
    if (template.assets().stream()
        .anyMatch(asset -> asset.kind() == ScaffoldAsset.Kind.DEPENDENCY)) {
      var dependencyContext = context(request, "dependency.add", "taf:dependency:write");
      if (request.dependencyApprovalId() == null
          || !approvals.permits(
              request.dependencyApprovalId(), dependencyContext, operationDigest)) {
        return result(ScaffoldResult.Status.APPROVAL_REQUIRED, "dependency approval required");
      }
    }
    var enforced =
        enforcement.enforce(
            new EnforcementRequest(
                request.requestId(),
                request.transport(),
                context(request, "scaffold", "taf:working-tree:write"),
                operationDigest,
                request.writeApprovalId(),
                Map.of("operation", "revert", "provenance", provenance.operationId())),
            () -> revertApproved(destination, provenance));
    return switch (enforced.status()) {
      case ALLOWED -> (ScaffoldResult) enforced.body();
      case APPROVAL_REQUIRED ->
          result(ScaffoldResult.Status.APPROVAL_REQUIRED, "write approval required");
      case DENIED -> result(ScaffoldResult.Status.DENIED, "authorization denied");
      case INPUT_REJECTED, FAILED ->
          result(ScaffoldResult.Status.FAILED, "scaffold revert failed safely");
    };
  }

  private synchronized ScaffoldResult revertApproved(Path root, ScaffoldProvenance provenance) {
    for (var file : provenance.files()) {
      var target = root.resolve(file.path()).normalize();
      if (!target.startsWith(root)
          || Files.isSymbolicLink(target)
          || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
          || !digest(read(target)).equals(file.sha256())) {
        return result(ScaffoldResult.Status.CONFLICT, "created files changed; revert refused");
      }
    }
    try {
      for (var file : provenance.files().reversed()) Files.delete(root.resolve(file.path()));
      return new ScaffoldResult(
          ScaffoldResult.Status.REVERTED, "scaffold reverted", List.of(), provenance);
    } catch (NoSuchFileException conflict) {
      return result(ScaffoldResult.Status.CONFLICT, "created files changed; revert refused");
    } catch (IOException failure) {
      return result(ScaffoldResult.Status.FAILED, "scaffold revert failed safely");
    }
  }

  private ScaffoldResult applyApproved(Path destination, ScaffoldTemplate template) {
    var targets =
        template.assets().stream()
            .map(asset -> destination.resolve(asset.path()).normalize())
            .toList();
    if (targets.stream()
        .anyMatch(
            target ->
                !target.startsWith(destination)
                    || Files.exists(target, LinkOption.NOFOLLOW_LINKS))) {
      return result(ScaffoldResult.Status.CONFLICT, "scaffold target already exists");
    }
    var created = new ArrayList<Path>();
    try {
      for (int index = 0; index < template.assets().size(); index++) {
        var asset = template.assets().get(index);
        var target = targets.get(index);
        Files.createDirectories(target.getParent());
        Files.writeString(
            target, asset.content(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        created.add(target);
      }
      var files = new ArrayList<ScaffoldProvenance.CreatedFile>();
      for (int index = 0; index < created.size(); index++) {
        var path = created.get(index);
        files.add(
            new ScaffoldProvenance.CreatedFile(
                destination.relativize(path),
                digest(read(path)),
                template.assets().get(index).kind()));
      }
      var provenance =
          new ScaffoldProvenance(
              UUID.randomUUID().toString(),
              template.name(),
              template.version(),
              clock.instant(),
              files);
      var diff =
          template.assets().stream()
              .map(
                  asset ->
                      "create "
                          + asset.path().toString().replace(java.io.File.separatorChar, '/')
                          + " ("
                          + asset.content().getBytes(StandardCharsets.UTF_8).length
                          + " bytes)")
              .toList();
      var compilation = compiler.compile(destination);
      return new ScaffoldResult(
          compilation.successful()
              ? ScaffoldResult.Status.APPLIED
              : ScaffoldResult.Status.COMPILATION_FAILED,
          compilation.successful() ? "scaffold compiled" : "scaffold compilation failed",
          diff,
          provenance);
    } catch (FileAlreadyExistsException conflict) {
      rollbackPartial(created);
      return result(ScaffoldResult.Status.CONFLICT, "scaffold target already exists");
    } catch (IOException failure) {
      rollbackPartial(created);
      return result(ScaffoldResult.Status.FAILED, "scaffold write failed safely");
    }
  }

  private Path confinedDestination(ScaffoldRequest request) {
    var workspace = confined(request.workspace());
    var destination =
        request.destination().isAbsolute()
            ? request.destination().normalize()
            : workspace.resolve(request.destination()).normalize();
    if (!destination.startsWith(workspace))
      throw new IllegalArgumentException("destination escapes workspace");
    return destination;
  }

  private Path confined(Path workspace) {
    var normalized = workspace.toAbsolutePath().normalize();
    if (!normalized.startsWith(approvedRoot)
        || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(normalized)) {
      throw new IllegalArgumentException("workspace is outside the approved root or unavailable");
    }
    return normalized;
  }

  private static AuthorizationContext context(
      ScaffoldRequest request, String action, String permission) {
    return new AuthorizationContext(
        request.identity(), request.projectId(), request.environment(), action, permission);
  }

  private static boolean matchesTemplate(ScaffoldTemplate template, ScaffoldProvenance provenance) {
    if (!template.name().equals(provenance.template())
        || !template.version().equals(provenance.templateVersion())
        || template.assets().size() != provenance.files().size()) {
      return false;
    }
    for (var asset : template.assets()) {
      if (provenance.files().stream()
          .noneMatch(file -> file.path().equals(asset.path()) && file.kind() == asset.kind())) {
        return false;
      }
    }
    return true;
  }

  private static String digest(ScaffoldRequest request, ScaffoldTemplate template) {
    var canonical =
        request.workspace().toAbsolutePath().normalize()
            + DIGEST_SEPARATOR
            + request.destination().normalize()
            + DIGEST_SEPARATOR
            + template.name()
            + DIGEST_SEPARATOR
            + template.version()
            + DIGEST_SEPARATOR
            + template.assets().stream()
                .map(asset -> asset.path() + ":" + digest(asset.content()))
                .sorted()
                .toList();
    return digest(canonical);
  }

  private static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static String read(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new IllegalStateException("created file is unavailable", failure);
    }
  }

  private static void rollbackPartial(List<Path> created) {
    for (var path : created.reversed()) {
      try {
        Files.deleteIfExists(path);
      } catch (IOException _) {
        // A later explicit revert remains guarded by provenance; partial failure has no provenance.
      }
    }
  }

  private static ScaffoldResult result(ScaffoldResult.Status status, String summary) {
    return new ScaffoldResult(status, summary, List.of(), null);
  }
}
