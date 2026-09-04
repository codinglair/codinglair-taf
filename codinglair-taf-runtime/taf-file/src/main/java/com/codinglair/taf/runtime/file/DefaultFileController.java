package com.codinglair.taf.runtime.file;

import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class DefaultFileController implements FileController {
  private final String name;
  private final Path root;
  private final long maximumSize;
  private final AtomicReference<ControllerState> state = new AtomicReference<>(ControllerState.NEW);
  private volatile ControllerContext context;
  private volatile StructuredFileComparator comparator;

  public DefaultFileController(String name, Path root, long maximumSize) {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("name must not be blank");
    this.name = name;
    this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
    this.maximumSize = maximumSize;
  }

  @Override
  public ControllerIdentity identity() {
    return new ControllerIdentity(FileController.class, name);
  }

  @Override
  public ControllerState state() {
    return state.get();
  }

  @Override
  public void initialize(ControllerContext value) {
    if (!state.compareAndSet(ControllerState.NEW, ControllerState.INITIALIZING))
      throw lifecycle("initialize", "create a fresh controller per session");
    context = Objects.requireNonNull(value);
    try {
      comparator = new StructuredFileComparator(new FileSandbox(root, maximumSize));
      state.set(ControllerState.READY);
    } catch (RuntimeException failure) {
      state.set(ControllerState.FAILED);
      throw failure;
    }
  }

  @Override
  public HealthResult health() {
    return state.get() == ControllerState.READY
        ? new HealthResult(
            HealthResult.Status.HEALTHY,
            "File validation controller is ready",
            Map.of("name", name, "maximumSize", Long.toString(maximumSize)))
        : HealthResult.unknown("File validation controller is not ready: " + state.get());
  }

  @Override
  public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
    return Stream.empty();
  }

  @Override
  public FileValidationResult compare(FileComparisonRequest request) {
    if (state.get() != ControllerState.READY)
      throw lifecycle("compare", "initialize the controller through TestSession before use");
    FileValidationResult result = comparator.compare(Objects.requireNonNull(request));
    context
        .artifacts()
        .addArtifact(
            "file-validation-" + context.artifacts().nextSequenceNumber() + ".txt",
            "file-validation-summary",
            result.evidence().asText(),
            "text/plain",
            null);
    return result;
  }

  @Override
  public void close() {
    if (state.getAndSet(ControllerState.CLOSED) == ControllerState.CLOSED) return;
    comparator = null;
    context = null;
  }

  private static FileValidationException lifecycle(String operation, String action) {
    return new FileValidationException(
        FileValidationException.Kind.LIFECYCLE, operation, action, null);
  }
}
