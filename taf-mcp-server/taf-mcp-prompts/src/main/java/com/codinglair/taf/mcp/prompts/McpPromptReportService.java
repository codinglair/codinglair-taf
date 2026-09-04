package com.codinglair.taf.mcp.prompts;

import com.codinglair.taf.mcp.resources.ResourceAccessDeniedException;
import com.codinglair.taf.mcp.resources.ResourceRequestContext;
import com.codinglair.taf.mcp.security.AuthorizationContext;
import com.codinglair.taf.mcp.security.EnforcementRequest;
import com.codinglair.taf.mcp.security.EnforcementResult;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.security.Transport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Audited boundary for prompts, report/evidence retrieval, and separately privileged diagnostics.
 */
public final class McpPromptReportService {
  public static final String REPORT_READ = "taf:report:read";
  public static final String ARTIFACT_READ = "taf:artifact:read";
  public static final String DIAGNOSTIC_READ = "taf:diagnostic:read";
  public static final int MAX_CHUNK_BYTES = 64 * 1024;

  private final PromptCatalog prompts;
  private final ReportRepository reports;
  private final McpEnforcementService enforcement;

  public McpPromptReportService(
      PromptCatalog prompts, ReportRepository reports, McpEnforcementService enforcement) {
    this.prompts = Objects.requireNonNull(prompts, "prompts");
    this.reports = Objects.requireNonNull(reports, "reports");
    this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
  }

  public List<PromptDefinition> prompts(
      ResourceRequestContext caller, String correlationId, Transport transport) {
    return allowed(
        caller, correlationId, transport, "prompts.list", REPORT_READ, Map.of(), prompts::all);
  }

  public PromptDefinition prompt(
      ResourceRequestContext caller, String correlationId, Transport transport, String name) {
    return allowed(
        caller,
        correlationId,
        transport,
        "prompts.get",
        REPORT_READ,
        Map.of("name", name),
        () -> prompts.find(name).orElseThrow(ResourceAccessDeniedException::new));
  }

  public ResourceChunk report(
      ResourceRequestContext caller,
      String correlationId,
      Transport transport,
      String jobId,
      String cursor) {
    return resource(caller, correlationId, transport, jobId, "report", cursor, REPORT_READ);
  }

  public ResourceChunk evidence(
      ResourceRequestContext caller,
      String correlationId,
      Transport transport,
      String jobId,
      String evidenceId,
      String cursor) {
    return resource(caller, correlationId, transport, jobId, evidenceId, cursor, ARTIFACT_READ);
  }

  public DiagnosticResult diagnose(
      ResourceRequestContext caller,
      String correlationId,
      Transport transport,
      String jobId,
      DiagnosticKind kind) {
    Objects.requireNonNull(kind, "kind");
    return allowed(
        caller,
        correlationId,
        transport,
        "diagnose." + kind.name().toLowerCase(java.util.Locale.ROOT),
        DIAGNOSTIC_READ,
        Map.of("jobId", jobId, "kind", kind.name()),
        () -> diagnostic(caller, jobId, kind));
  }

  private ResourceChunk resource(
      ResourceRequestContext caller,
      String correlationId,
      Transport transport,
      String jobId,
      String resourceId,
      String cursor,
      String permission) {
    TextBounds.requireIdentifier(jobId, "jobId");
    TextBounds.requireIdentifier(resourceId, "resourceId");
    return allowed(
        caller,
        correlationId,
        transport,
        "resource.retrieve",
        permission,
        Map.of("jobId", jobId, "resourceId", resourceId),
        () -> chunk(find(caller, jobId, resourceId), cursor));
  }

  private StoredResource find(ResourceRequestContext caller, String jobId, String resourceId) {
    return reports.findByJobId(jobId).stream()
        .filter(item -> item.jobId().equals(jobId))
        .filter(item -> item.resourceId().equals(resourceId))
        .filter(item -> item.access().permits(caller))
        .findFirst()
        .orElseThrow(ResourceAccessDeniedException::new);
  }

  private ResourceChunk chunk(StoredResource resource, String cursor) {
    if (!resource.mediaType().startsWith("text/")
        && !resource.mediaType().equals("application/json")) {
      return new ResourceChunk(
          "1.0", resource.uri(), resource.mediaType(), 0, "", resource.redacted(), null, true);
    }
    var sanitizedContent = (String) enforcement.redact(resource.content());
    var bytes = sanitizedContent.getBytes(StandardCharsets.UTF_8);
    var offset = decodeCursor(cursor, resource.uri());
    if (offset > bytes.length) {
      throw new IllegalArgumentException("resource cursor is invalid");
    }
    var end = Math.min(bytes.length, offset + MAX_CHUNK_BYTES);
    while (end > offset && end < bytes.length && (bytes[end] & 0xc0) == 0x80) {
      end--;
    }
    var content = new String(bytes, offset, end - offset, StandardCharsets.UTF_8);
    var next = end < bytes.length ? encodeCursor(end, resource.uri()) : null;
    return new ResourceChunk(
        "1.0",
        resource.uri(),
        resource.mediaType(),
        content.getBytes(StandardCharsets.UTF_8).length,
        content,
        resource.redacted(),
        next,
        false);
  }

  private DiagnosticResult diagnostic(
      ResourceRequestContext caller, String jobId, DiagnosticKind kind) {
    TextBounds.requireIdentifier(jobId, "jobId");
    var visible =
        reports.findByJobId(jobId).stream()
            .filter(item -> item.jobId().equals(jobId) && item.access().permits(caller))
            .sorted(Comparator.comparing(StoredResource::uri))
            .limit(100)
            .toList();
    if (visible.isEmpty()) {
      throw new ResourceAccessDeniedException();
    }
    var references = visible.stream().map(StoredResource::uri).toList();
    var summary =
        switch (kind) {
          case REPORT_METADATA ->
              Map.of("resources", Integer.toString(visible.size()), "status", "available");
          case EVIDENCE_INVENTORY ->
              Map.of(
                  "evidenceCount",
                  Long.toString(
                      visible.stream()
                          .filter(item -> item.uri().startsWith("taf://evidence/"))
                          .count()),
                  "truncated",
                  Boolean.toString(visible.size() == 100));
          case FAILURE_SUMMARY ->
              Map.of(
                  "reports",
                  Long.toString(
                      visible.stream()
                          .filter(item -> item.uri().startsWith("taf://report/"))
                          .count()),
                  "detail",
                  "inspect controlled report references");
        };
    return new DiagnosticResult(kind, jobId, summary, references);
  }

  @SuppressWarnings("unchecked")
  private <T> T allowed(
      ResourceRequestContext caller,
      String correlationId,
      Transport transport,
      String action,
      String permission,
      Object input,
      java.util.concurrent.Callable<T> operation) {
    Objects.requireNonNull(caller, "caller");
    var authorization =
        new AuthorizationContext(
            caller.identity(), caller.project(), caller.environment(), action, permission);
    var request =
        new EnforcementRequest(
            correlationId, transport, authorization, digest(action, input), null, input);
    var result = enforcement.enforce(request, operation);
    if (result.status() != EnforcementResult.Status.ALLOWED) {
      throw new ResourceAccessDeniedException();
    }
    return (T) result.body();
  }

  private static String encodeCursor(int offset, String uri) {
    return offset + ":" + digest("cursor", uri).substring(0, 16);
  }

  private static int decodeCursor(String cursor, String uri) {
    if (cursor == null || cursor.isBlank()) {
      return 0;
    }
    var expectedSuffix = digest("cursor", uri).substring(0, 16);
    var parts = cursor.split(":", -1);
    if (parts.length != 2 || !parts[1].equals(expectedSuffix)) {
      throw new IllegalArgumentException("resource cursor is invalid");
    }
    try {
      return Integer.parseInt(parts[0]);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("resource cursor is invalid", exception);
    }
  }

  private static String digest(String action, Object value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest((action + ":" + value).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }
}
