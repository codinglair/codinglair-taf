package com.codinglair.taf.mcp.tools;

import com.codinglair.taf.mcp.jobs.JobId;
import com.codinglair.taf.mcp.worker.CancellationToken;
import com.codinglair.taf.mcp.worker.LocalExecutionWorker;
import com.codinglair.taf.mcp.worker.WorkerExecutionException;
import com.codinglair.taf.mcp.worker.WorkerProtocol;
import com.codinglair.taf.mcp.worker.WorkerRequest;
import java.util.List;
import java.util.Objects;

/** Adapts validated coarse-grained inputs to the fixed, administrator-defined worker workflows. */
public final class WorkerWorkflowRunner implements WorkflowRunner {
  private final LocalExecutionWorker worker;
  private final CancellationToken cancellation;
  private final WorkflowNameResolver workflows;

  public WorkerWorkflowRunner(
      LocalExecutionWorker worker, CancellationToken cancellation, WorkflowNameResolver workflows) {
    this.worker = Objects.requireNonNull(worker, "worker");
    this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
    this.workflows = Objects.requireNonNull(workflows, "workflows");
  }

  @Override
  public WorkflowResult run(JobId jobId, ToolRequest request) {
    try {
      var result =
          worker.execute(
              new WorkerRequest(
                  WorkerProtocol.VERSION,
                  jobId,
                  workflows.resolve(request.operation(), request.environment(), request.selector()),
                  request.workspace(),
                  request.timeout(),
                  List.of("target/surefire-reports", "target/failsafe-reports"),
                  request.requestId()),
              cancellation);
      var outcome =
          switch (result.status()) {
            case SUCCEEDED -> WorkflowOutcome.SUCCEEDED;
            case CANCELLED -> WorkflowOutcome.CANCELLED;
            case TIMED_OUT -> WorkflowOutcome.TIMED_OUT;
            case FAILED ->
                request.operation() == ToolOperation.EXECUTE
                    ? WorkflowOutcome.TEST_FAILED
                    : WorkflowOutcome.COMPILE_FAILED;
          };
      return new WorkflowResult(
          outcome,
          result.outputSummary().isBlank()
              ? outcome.name().toLowerCase(java.util.Locale.ROOT)
              : result.outputSummary(),
          result.artifacts().stream().map(entry -> entry.reference()).toList());
    } catch (WorkerExecutionException failure) {
      var environment =
          switch (failure.code()) {
            case "INVALID_WORKSPACE", "WORKSPACE_LIMIT", "PATH_ESCAPE" -> true;
            default -> false;
          };
      return new WorkflowResult(
          environment ? WorkflowOutcome.ENVIRONMENT_FAILED : WorkflowOutcome.INTERNAL_FAILED,
          environment ? "execution environment is unavailable" : "workflow failed safely",
          List.of());
    }
  }
}
