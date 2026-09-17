package com.codinglair.taf.mcp.tools;

import com.codinglair.taf.mcp.security.ApprovalService;
import com.codinglair.taf.mcp.security.AuthorizationContext;
import com.codinglair.taf.mcp.security.EnforcementRequest;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/** Governed MCP orchestration over the authoritative blueprint catalog and composition engine. */
public final class BlueprintScaffoldingService {
  private final Path approvedRoot;
  private final BlueprintCompositionEngine engine;
  private final McpEnforcementService enforcement;
  private final ApprovalService approvals;
  private final ScaffoldCompiler preflight;

  public BlueprintScaffoldingService(
      Path approvedRoot,
      BlueprintCompositionEngine engine,
      McpEnforcementService enforcement,
      ApprovalService approvals,
      ScaffoldCompiler preflight) {
    this.approvedRoot = approvedRoot.toAbsolutePath().normalize();
    this.engine = Objects.requireNonNull(engine, "engine");
    this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
    this.approvals = Objects.requireNonNull(approvals, "approvals");
    this.preflight = Objects.requireNonNull(preflight, "preflight");
  }

  public BlueprintScaffoldResult execute(BlueprintScaffoldRequest request) {
    Objects.requireNonNull(request, "request");
    Path destination = confined(request.destination());
    var plan = plan(request, destination);
    if (!plan.valid())
      return result(
          BlueprintScaffoldResult.Status.VALIDATION_FAILED, plan, "request validation failed");
    if (!request.publish())
      return result(BlueprintScaffoldResult.Status.VALIDATED, plan, "blueprint validated");

    String digest = digest(plan);
    var dependencyContext = context(request, "dependency.add", "taf:dependency:write");
    if (request.dependencyApprovalId() == null
        || !approvals.permits(request.dependencyApprovalId(), dependencyContext, digest)) {
      return result(
          BlueprintScaffoldResult.Status.APPROVAL_REQUIRED, plan, "dependency approval required");
    }
    var enforced =
        enforcement.enforce(
            new EnforcementRequest(
                request.requestId(),
                request.transport(),
                context(request, "scaffold", "taf:working-tree:write"),
                digest,
                request.writeApprovalId(),
                Map.of(
                    "blueprintVersion", plan.blueprintVersion(),
                    "capabilities",
                        plan.request().capabilities().stream().map(Enum::name).toList())),
            () -> publish(plan, destination));
    return switch (enforced.status()) {
      case ALLOWED -> (BlueprintScaffoldResult) enforced.body();
      case APPROVAL_REQUIRED ->
          result(BlueprintScaffoldResult.Status.APPROVAL_REQUIRED, plan, "write approval required");
      case DENIED -> result(BlueprintScaffoldResult.Status.DENIED, plan, "authorization denied");
      case INPUT_REJECTED, FAILED ->
          result(BlueprintScaffoldResult.Status.FAILED, plan, "scaffold failed safely");
    };
  }

  public String requestWriteApproval(BlueprintScaffoldRequest request, Duration ttl) {
    var plan = validPlan(request);
    return approvals
        .request(context(request, "scaffold", "taf:working-tree:write"), digest(plan), ttl)
        .id();
  }

  public String requestDependencyApproval(BlueprintScaffoldRequest request, Duration ttl) {
    var plan = validPlan(request);
    return approvals
        .request(context(request, "dependency.add", "taf:dependency:write"), digest(plan), ttl)
        .id();
  }

  private BlueprintCompositionEngine.CompositionPlan validPlan(BlueprintScaffoldRequest request) {
    var plan = plan(request);
    if (!plan.valid()) throw new IllegalArgumentException("blueprint request is invalid");
    return plan;
  }

  private BlueprintCompositionEngine.CompositionPlan plan(BlueprintScaffoldRequest request) {
    Objects.requireNonNull(request, "request");
    return plan(request, confined(request.destination()));
  }

  private BlueprintCompositionEngine.CompositionPlan plan(
      BlueprintScaffoldRequest request, Path destination) {
    return engine.plan(
        request.blueprint(),
        CapabilityContributionCatalog.BLUEPRINT_VERSION,
        CapabilityContributionCatalog.contributions(),
        CapabilityContributionCatalog.starterManifest(),
        destination);
  }

  private BlueprintScaffoldResult publish(
      BlueprintCompositionEngine.CompositionPlan plan, Path destination) {
    var write = engine.write(plan, destination);
    if (write.status() == BlueprintCompositionEngine.WriteStatus.CONFLICT)
      return result(BlueprintScaffoldResult.Status.CONFLICT, plan, "destination already exists");
    if (write.status() != BlueprintCompositionEngine.WriteStatus.WRITTEN)
      return result(BlueprintScaffoldResult.Status.FAILED, plan, "scaffold publication failed");
    var checked = preflight.compile(destination);
    return checked.successful()
        ? result(
            BlueprintScaffoldResult.Status.APPLIED, plan, "scaffold applied and preflight passed")
        : result(
            BlueprintScaffoldResult.Status.PREFLIGHT_FAILED,
            plan,
            "generated project preflight failed");
  }

  private Path confined(Path requested) {
    Path destination =
        requested.isAbsolute()
            ? requested.normalize()
            : approvedRoot.resolve(requested).normalize();
    if (!destination.startsWith(approvedRoot) || destination.equals(approvedRoot))
      throw new IllegalArgumentException("destination is outside the approved root");
    Path parent = destination.getParent();
    if (parent == null
        || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(parent))
      throw new IllegalArgumentException("destination parent is unavailable or unsafe");
    return destination;
  }

  private static AuthorizationContext context(
      BlueprintScaffoldRequest request, String action, String permission) {
    return new AuthorizationContext(
        request.identity(), request.projectId(), request.environment(), action, permission);
  }

  private static String digest(BlueprintCompositionEngine.CompositionPlan plan) {
    try {
      var canonical =
          plan.blueprintVersion()
              + '\u001f'
              + String.join(";", plan.contributionIds())
              + '\u001f'
              + plan.writes().stream()
                  .map(write -> write.path() + ":" + write.sha256())
                  .reduce("", (a, b) -> a + "\u001f" + b);
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static BlueprintScaffoldResult result(
      BlueprintScaffoldResult.Status status,
      BlueprintCompositionEngine.CompositionPlan plan,
      String summary) {
    return new BlueprintScaffoldResult(
        status,
        plan.blueprintVersion(),
        plan.contributionIds(),
        plan.writes().stream().map(write -> write.path().toString().replace('\\', '/')).toList(),
        plan.diagnostics(),
        summary);
  }
}
