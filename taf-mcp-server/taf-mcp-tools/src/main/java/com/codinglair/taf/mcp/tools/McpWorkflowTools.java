package com.codinglair.taf.mcp.tools;

import com.codinglair.taf.mcp.jobs.Job;
import com.codinglair.taf.mcp.jobs.JobConflictException;
import com.codinglair.taf.mcp.jobs.JobId;
import com.codinglair.taf.mcp.jobs.JobRepository;
import com.codinglair.taf.mcp.jobs.JobService;
import com.codinglair.taf.mcp.jobs.JobState;
import com.codinglair.taf.mcp.jobs.JobStateMachine;
import com.codinglair.taf.mcp.security.AuthorizationContext;
import com.codinglair.taf.mcp.security.EnforcementRequest;
import com.codinglair.taf.mcp.security.EnforcementResult;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.security.ResponseRedactor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

/** Transport-neutral handlers for the accepted validate/build/execute MCP vertical slice. */
public final class McpWorkflowTools implements AutoCloseable {
  private static final Pattern SELECTOR = Pattern.compile("[A-Za-z0-9_.$:#*?,-]{1,256}");
  private final Path workspaceRoot;
  private final McpEnforcementService enforcement;
  private final JobService jobs;
  private final JobRepository repository;
  private final ProjectValidator validator;
  private final WorkflowRunner runner;
  private final ExecutorService executor;
  private final Clock clock;
  private final ResponseRedactor redactor;

  public McpWorkflowTools(
      Path workspaceRoot,
      McpEnforcementService enforcement,
      JobService jobs,
      JobRepository repository,
      ProjectValidator validator,
      WorkflowRunner runner,
      ExecutorService executor,
      Clock clock,
      ResponseRedactor redactor) {
    this.workspaceRoot =
        Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
    this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
    this.jobs = Objects.requireNonNull(jobs, "jobs");
    this.repository = Objects.requireNonNull(repository, "repository");
    this.validator = Objects.requireNonNull(validator, "validator");
    this.runner = Objects.requireNonNull(runner, "runner");
    this.executor = Objects.requireNonNull(executor, "executor");
    this.clock = Clock.tick(Objects.requireNonNull(clock, "clock"), java.time.Duration.ofMillis(1));
    this.redactor = Objects.requireNonNull(redactor, "redactor");
  }

  public List<String> discover() {
    return List.of("validate", "compile", "build", "execute", "cancel");
  }

  public ToolResponse invoke(ToolRequest request) {
    Objects.requireNonNull(request, "request");
    var context =
        new AuthorizationContext(
            request.identity(),
            request.projectId(),
            request.environment(),
            request.operation().action(),
            request.operation().permission());
    var result =
        enforcement.enforce(
            new EnforcementRequest(
                request.requestId(),
                request.transport(),
                context,
                digest(request),
                request.approvalId(),
                safeInput(request)),
            () -> {
              try {
                validateBoundary(request);
                return dispatch(request);
              } catch (IllegalArgumentException failure) {
                return failed(request, WorkflowOutcome.VALIDATION_FAILED, failure.getMessage());
              }
            });
    if (result.status() != EnforcementResult.Status.ALLOWED) {
      var outcome =
          switch (result.status()) {
            case DENIED -> WorkflowOutcome.DENIED;
            case APPROVAL_REQUIRED -> WorkflowOutcome.APPROVAL_REQUIRED;
            case INPUT_REJECTED -> WorkflowOutcome.VALIDATION_FAILED;
            case FAILED -> WorkflowOutcome.INTERNAL_FAILED;
            case ALLOWED -> throw new IllegalStateException();
          };
      return failed(
          request,
          outcome,
          switch (result.status()) {
            case DENIED -> "authorization denied";
            case APPROVAL_REQUIRED -> "approval required";
            case INPUT_REJECTED -> "input rejected";
            case FAILED -> "operation failed";
            case ALLOWED -> throw new IllegalStateException();
          });
    }
    return (ToolResponse) result.body();
  }

  private ToolResponse dispatch(ToolRequest request) {
    return switch (request.operation()) {
      case VALIDATE -> response(request, validator.validate(request));
      case CANCEL -> cancel(request);
      case COMPILE, BUILD, EXECUTE -> submit(request);
    };
  }

  private ToolResponse submit(ToolRequest request) {
    Job job =
        jobs.create(
            request.operation().action(),
            Map.of(
                "project",
                sanitize(request.projectId()),
                "environment",
                sanitize(request.environment()),
                "selector",
                sanitize(request.selector().orElse("all")),
                "requestId",
                sanitize(request.requestId())));
    try {
      executor.execute(() -> run(job.id(), request));
    } catch (RuntimeException submissionFailure) {
      var cancellationRequested = jobs.cancel(job.id());
      repository.save(
          JobStateMachine.transition(cancellationRequested, JobState.CANCELLED, clock.instant()),
          cancellationRequested.version());
      return failed(request, WorkflowOutcome.INTERNAL_FAILED, "workflow scheduling failed");
    }
    return new ToolResponse(
        request.requestId(),
        ToolResponse.Status.ACCEPTED,
        null,
        "workflow accepted",
        "taf://job/" + job.id().value(),
        List.of());
  }

  private void run(JobId id, ToolRequest request) {
    Job current = repository.find(id).orElseThrow();
    try {
      current =
          repository.save(
              JobStateMachine.transition(current, JobState.RUNNING, clock.instant()),
              current.version());
      WorkflowResult result = runner.run(id, request);
      current =
          repository.save(
              JobStateMachine.progress(
                  current, 99, "workflow.outcome", result.outcome().name(), clock.instant()),
              current.version());
      if (result.outcome() == WorkflowOutcome.CANCELLED) {
        current =
            repository.save(
                JobStateMachine.transition(current, JobState.CANCEL_REQUESTED, clock.instant()),
                current.version());
      }
      Job terminal =
          switch (result.outcome()) {
            case SUCCEEDED ->
                JobStateMachine.succeed(current, result.references(), clock.instant());
            case CANCELLED ->
                JobStateMachine.transition(current, JobState.CANCELLED, clock.instant());
            default -> JobStateMachine.transition(current, JobState.FAILED, clock.instant());
          };
      repository.save(terminal, current.version());
    } catch (JobConflictException conflict) {
      repository
          .find(id)
          .filter(job -> job.state() == JobState.CANCEL_REQUESTED)
          .ifPresent(
              job ->
                  repository.save(
                      JobStateMachine.transition(job, JobState.CANCELLED, clock.instant()),
                      job.version()));
    } catch (RuntimeException failure) {
      failRunningJob(id);
    }
  }

  private void failRunningJob(JobId id) {
    repository
        .find(id)
        .filter(job -> job.state() == JobState.RUNNING)
        .ifPresent(
            job -> {
              var classified =
                  repository.save(
                      JobStateMachine.progress(
                          job,
                          job.progressPercent(),
                          "workflow.outcome",
                          WorkflowOutcome.INTERNAL_FAILED.name(),
                          clock.instant()),
                      job.version());
              repository.save(
                  JobStateMachine.transition(classified, JobState.FAILED, clock.instant()),
                  classified.version());
            });
  }

  private ToolResponse cancel(ToolRequest request) {
    var id = new JobId(request.targetJobId());
    var target =
        repository
            .find(id)
            .orElseThrow(() -> new java.util.NoSuchElementException("job unavailable"));
    if (!sanitize(request.projectId()).equals(target.payload().get("project"))
        || !sanitize(request.environment()).equals(target.payload().get("environment"))) {
      throw new java.util.NoSuchElementException("job unavailable");
    }
    Job job = jobs.cancel(id);
    WorkflowOutcome outcome = job.state() == JobState.CANCELLED ? WorkflowOutcome.CANCELLED : null;
    return new ToolResponse(
        request.requestId(),
        ToolResponse.Status.COMPLETED,
        outcome,
        "cancellation state: " + job.state().name().toLowerCase(java.util.Locale.ROOT),
        "taf://job/" + job.id().value(),
        job.resultReferences().stream().map(r -> r.uri()).toList());
  }

  private void validateBoundary(ToolRequest request) {
    Path workspace = request.workspace().toAbsolutePath().normalize();
    if (!workspace.startsWith(workspaceRoot)
        || !Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(workspace))
      throw new IllegalArgumentException("workspace is outside the approved root or unavailable");
    if (request.selector().isPresent()
        && !SELECTOR.matcher(request.selector().orElseThrow()).matches())
      throw new IllegalArgumentException("selector contains unsupported characters");
    if (request.timeout().toSeconds() > request.operation().maximumTimeoutSeconds())
      throw new IllegalArgumentException("timeout exceeds the operation limit");
    if (!sanitize(request.requestId()).equals(request.requestId())
        || !sanitize(request.projectId()).equals(request.projectId())
        || !sanitize(request.environment()).equals(request.environment())
        || request.selector().filter(value -> !sanitize(value).equals(value)).isPresent())
      throw new IllegalArgumentException("request contains sensitive data");
    if (request.operation() == ToolOperation.CANCEL
        && (request.targetJobId() == null || request.targetJobId().isBlank()))
      throw new IllegalArgumentException("targetJobId is required for cancellation");
  }

  private Map<String, Object> safeInput(ToolRequest request) {
    @SuppressWarnings("unchecked")
    var sanitized =
        (Map<String, Object>)
            redactor.redact(
                Map.of(
                    "operation",
                    request.operation().action(),
                    "project",
                    request.projectId(),
                    "environment",
                    request.environment(),
                    "timeoutSeconds",
                    request.timeout().toSeconds(),
                    "selector",
                    request.selector().orElse("all")));
    return sanitized;
  }

  private static String digest(ToolRequest request) {
    try {
      var canonical =
          request.operation().action()
              + '\u001f'
              + request.projectId()
              + '\u001f'
              + request.environment()
              + '\u001f'
              + request.workspace().toAbsolutePath().normalize()
              + '\u001f'
              + request.selector().orElse("")
              + '\u001f'
              + request.timeout().toSeconds()
              + '\u001f'
              + String.valueOf(request.idempotencyKey());
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private ToolResponse response(ToolRequest request, WorkflowResult result) {
    var status =
        result.outcome() == WorkflowOutcome.SUCCEEDED
            ? ToolResponse.Status.COMPLETED
            : ToolResponse.Status.FAILED;
    return new ToolResponse(
        request.requestId(),
        status,
        result.outcome(),
        sanitize(result.summary()),
        null,
        result.references().stream().map(r -> r.uri()).toList());
  }

  private ToolResponse failed(ToolRequest request, WorkflowOutcome outcome, String summary) {
    return new ToolResponse(
        request.requestId(),
        ToolResponse.Status.FAILED,
        outcome,
        sanitize(summary),
        null,
        List.of());
  }

  private String sanitize(String value) {
    return (String) redactor.redact(value);
  }

  @Override
  public void close() {
    executor.shutdownNow();
    try {
      executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
