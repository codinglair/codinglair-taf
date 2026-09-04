package com.codinglair.taf.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mcp.security.ApprovalService;
import com.codinglair.taf.mcp.security.AuthorizationPolicyEngine;
import com.codinglair.taf.mcp.security.CallerIdentity;
import com.codinglair.taf.mcp.security.IdentityKind;
import com.codinglair.taf.mcp.security.InMemoryAuditLog;
import com.codinglair.taf.mcp.security.McpEnforcementService;
import com.codinglair.taf.mcp.security.PolicyRule;
import com.codinglair.taf.mcp.security.ResponseRedactor;
import com.codinglair.taf.mcp.security.Transport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScaffoldWorkflowTest {
  private static final CallerIdentity REQUESTER =
      new CallerIdentity("requester", Set.of("developer"), "agent", IdentityKind.AGENT);
  private static final CallerIdentity APPROVER =
      new CallerIdentity("approver", Set.of("lead"), "human", IdentityKind.HUMAN);

  @TempDir Path temporary;

  @Test
  void scaffoldCompilesAndProducesVersionedProvenanceAndDiff() throws Exception {
    var fixture = fixture(template(false), ScaffoldWorkflowTest::compileJava);
    var workspace = Files.createDirectory(temporary.resolve("workspace"));
    var request = request(workspace, "generated", null, null);
    request = approved(fixture, request, false);

    var result = fixture.workflow.apply(request);

    assertThat(result.status()).isEqualTo(ScaffoldResult.Status.APPLIED);
    assertThat(result.summary()).isEqualTo("scaffold compiled");
    assertThat(result.proposedDiff())
        .containsExactly("create src/main/java/example/Generated.java (50 bytes)");
    assertThat(result.provenance().templateVersion()).isEqualTo("1.0");
    assertThat(
            Files.readString(workspace.resolve("generated/src/main/java/example/Generated.java")))
        .contains("class Generated");
  }

  @Test
  void dirtyTreeIsPreservedAndExistingTargetConflicts() throws Exception {
    var fixture =
        fixture(template(false), _ -> new ScaffoldCompiler.CompilationResult(true, "compiled"));
    var workspace = Files.createDirectory(temporary.resolve("dirty"));
    var unrelated = workspace.resolve("user-change.txt");
    Files.writeString(unrelated, "keep-me");
    var first = approved(fixture, request(workspace, "generated", null, null), false);

    assertThat(fixture.workflow.apply(first).status()).isEqualTo(ScaffoldResult.Status.APPLIED);
    var second = approved(fixture, request(workspace, "generated", null, null), false);
    assertThat(fixture.workflow.apply(second).status()).isEqualTo(ScaffoldResult.Status.CONFLICT);
    assertThat(Files.readString(unrelated)).isEqualTo("keep-me");
  }

  @Test
  void dependencyRequiresAnIndependentApproval() throws Exception {
    var fixture =
        fixture(template(true), _ -> new ScaffoldCompiler.CompilationResult(true, "compiled"));
    var workspace = Files.createDirectory(temporary.resolve("dependency"));
    var request = request(workspace, "generated", null, null);
    var write = fixture.workflow.requestWriteApproval(request, Duration.ofMinutes(5));
    fixture.approvals.decide(write.id(), APPROVER, true);
    request = request(workspace, "generated", write.id(), null);

    assertThat(fixture.workflow.apply(request).status())
        .isEqualTo(ScaffoldResult.Status.APPROVAL_REQUIRED);
    assertThat(Files.exists(workspace.resolve("generated"))).isFalse();

    var dependency = fixture.workflow.requestDependencyApproval(request, Duration.ofMinutes(5));
    fixture.approvals.decide(dependency.id(), APPROVER, true);
    request = request(workspace, "generated", write.id(), dependency.id());
    assertThat(fixture.workflow.apply(request).status()).isEqualTo(ScaffoldResult.Status.APPLIED);
  }

  @Test
  void revertRemovesOnlyUnchangedCreatedFiles() throws Exception {
    var fixture =
        fixture(template(false), _ -> new ScaffoldCompiler.CompilationResult(true, "compiled"));
    var workspace = Files.createDirectory(temporary.resolve("revert"));
    var request = approved(fixture, request(workspace, "generated", null, null), false);
    var applied = fixture.workflow.apply(request);
    var generated = workspace.resolve("generated/src/main/java/example/Generated.java");
    Files.writeString(generated, "user edited");

    assertThat(fixture.workflow.revert(request, applied.provenance()).status())
        .isEqualTo(ScaffoldResult.Status.CONFLICT);
    assertThat(Files.readString(generated)).isEqualTo("user edited");

    Files.writeString(generated, template(false).assets().getFirst().content());
    assertThat(fixture.workflow.revert(request, applied.provenance()).status())
        .isEqualTo(ScaffoldResult.Status.REVERTED);
    assertThat(generated).doesNotExist();
  }

  @Test
  void concurrentScaffoldsHaveOneWinnerWithoutOverwriting() throws Exception {
    var fixture =
        fixture(template(false), _ -> new ScaffoldCompiler.CompilationResult(true, "compiled"));
    var workspace = Files.createDirectory(temporary.resolve("concurrent"));
    var first = approved(fixture, request(workspace, "generated", null, null), false);
    var second = approved(fixture, request(workspace, "generated", null, null), false);
    try (var executor = Executors.newFixedThreadPool(2)) {
      List<Callable<ScaffoldResult>> calls =
          List.of(() -> fixture.workflow.apply(first), () -> fixture.workflow.apply(second));
      var results = executor.invokeAll(calls);
      assertThat(results.stream().map(future -> result(future).status()).toList())
          .containsExactlyInAnyOrder(ScaffoldResult.Status.APPLIED, ScaffoldResult.Status.CONFLICT);
    }
  }

  @Test
  void compilationFailureRemainsARevertibleProposedChange() throws Exception {
    var fixture =
        fixture(
            template(false), _ -> new ScaffoldCompiler.CompilationResult(false, "compile failed"));
    var workspace = Files.createDirectory(temporary.resolve("compile-failure"));
    var request = approved(fixture, request(workspace, "generated", null, null), false);

    var result = fixture.workflow.apply(request);

    assertThat(result.status()).isEqualTo(ScaffoldResult.Status.COMPILATION_FAILED);
    assertThat(result.provenance()).isNotNull();
    assertThat(fixture.workflow.revert(request, result.provenance()).status())
        .isEqualTo(ScaffoldResult.Status.REVERTED);
  }

  @Test
  void destinationTraversalIsRejectedBeforeApprovalOrWrite() throws Exception {
    var fixture = fixture(template(false), ScaffoldWorkflowTest::compileJava);
    var workspace = Files.createDirectory(temporary.resolve("traversal"));
    var request = request(workspace, "../escape", null, null);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> fixture.workflow.requestWriteApproval(request, Duration.ofMinutes(5)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("escapes workspace");
    assertThat(temporary.resolve("escape")).doesNotExist();
  }

  @Test
  void dependencyRollbackRequiresBothOriginalApprovals() throws Exception {
    var fixture =
        fixture(template(true), _ -> new ScaffoldCompiler.CompilationResult(true, "compiled"));
    var workspace = Files.createDirectory(temporary.resolve("dependency-revert"));
    var approved = approved(fixture, request(workspace, "generated", null, null), true);
    var applied = fixture.workflow.apply(approved);
    var writeOnly = request(workspace, "generated", approved.writeApprovalId(), null);

    assertThat(fixture.workflow.revert(writeOnly, applied.provenance()).status())
        .isEqualTo(ScaffoldResult.Status.APPROVAL_REQUIRED);
    assertThat(workspace.resolve("generated/pom.xml")).exists();

    var dependencyOnly = request(workspace, "generated", null, approved.dependencyApprovalId());
    assertThat(fixture.workflow.revert(dependencyOnly, applied.provenance()).status())
        .isEqualTo(ScaffoldResult.Status.APPROVAL_REQUIRED);
    assertThat(workspace.resolve("generated/src/main/java/example/Generated.java")).exists();

    assertThat(fixture.workflow.revert(approved, applied.provenance()).status())
        .isEqualTo(ScaffoldResult.Status.REVERTED);
  }

  @Test
  void concurrentRevertsHaveOneWinnerWithoutDeletingUnownedContent() throws Exception {
    var fixture =
        fixture(template(false), _ -> new ScaffoldCompiler.CompilationResult(true, "compiled"));
    var workspace = Files.createDirectory(temporary.resolve("concurrent-revert"));
    var unrelated = workspace.resolve("user.txt");
    Files.writeString(unrelated, "keep");
    var request = approved(fixture, request(workspace, "generated", null, null), false);
    var applied = fixture.workflow.apply(request);

    try (var executor = Executors.newFixedThreadPool(2)) {
      List<Callable<ScaffoldResult>> calls =
          List.of(
              () -> fixture.workflow.revert(request, applied.provenance()),
              () -> fixture.workflow.revert(request, applied.provenance()));
      var results = executor.invokeAll(calls);
      assertThat(results.stream().map(future -> result(future).status()).toList())
          .containsExactlyInAnyOrder(
              ScaffoldResult.Status.REVERTED, ScaffoldResult.Status.CONFLICT);
    }
    assertThat(Files.readString(unrelated)).isEqualTo("keep");
  }

  @Test
  void provenanceRejectsAnEmptyCreatedFileSet() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                new ScaffoldProvenance(
                    "operation", "template", "1.0", java.time.Instant.EPOCH, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one");
  }

  @Test
  void rollbackRejectsProvenanceWithTamperedAssetKind() throws Exception {
    var fixture =
        fixture(template(true), _ -> new ScaffoldCompiler.CompilationResult(true, "compiled"));
    var workspace = Files.createDirectory(temporary.resolve("tampered-provenance"));
    var request = approved(fixture, request(workspace, "generated", null, null), true);
    var applied = fixture.workflow.apply(request);
    var original = applied.provenance();
    var files =
        original.files().stream()
            .map(
                file ->
                    new ScaffoldProvenance.CreatedFile(
                        file.path(),
                        file.sha256(),
                        file.kind() == ScaffoldAsset.Kind.DEPENDENCY
                            ? ScaffoldAsset.Kind.PROJECT
                            : file.kind()))
            .toList();
    var tampered =
        new ScaffoldProvenance(
            original.operationId(),
            original.template(),
            original.templateVersion(),
            original.createdAt(),
            files);

    assertThat(fixture.workflow.revert(request, tampered).status())
        .isEqualTo(ScaffoldResult.Status.CONFLICT);
    assertThat(workspace.resolve("generated/pom.xml")).exists();
  }

  @Test
  void partialWriteFailureRemovesOnlyFilesCreatedByThatAttempt() throws Exception {
    var template =
        new ScaffoldTemplate(
            "partial-project",
            "1.0",
            List.of(
                new ScaffoldAsset(
                    Path.of("created.txt"), "temporary\n", ScaffoldAsset.Kind.PROJECT),
                new ScaffoldAsset(
                    Path.of("blocked/child.txt"), "never-written\n", ScaffoldAsset.Kind.PROJECT)));
    var fixture = fixture(template, _ -> new ScaffoldCompiler.CompilationResult(true, "compiled"));
    var workspace = Files.createDirectory(temporary.resolve("partial-write"));
    var destination = Files.createDirectory(workspace.resolve("generated"));
    var blocker = destination.resolve("blocked");
    Files.writeString(blocker, "unrelated-user-file");
    var initial =
        new ScaffoldRequest(
            "request",
            "project",
            "local",
            workspace,
            Path.of("generated"),
            "partial-project",
            "1.0",
            null,
            null,
            REQUESTER,
            Transport.INTERNAL);
    var write = fixture.workflow.requestWriteApproval(initial, Duration.ofMinutes(5));
    fixture.approvals.decide(write.id(), APPROVER, true);
    var approved =
        new ScaffoldRequest(
            initial.requestId(),
            initial.projectId(),
            initial.environment(),
            initial.workspace(),
            initial.destination(),
            initial.template(),
            initial.templateVersion(),
            write.id(),
            null,
            initial.identity(),
            initial.transport());

    assertThat(fixture.workflow.apply(approved).status()).isEqualTo(ScaffoldResult.Status.CONFLICT);
    assertThat(destination.resolve("created.txt")).doesNotExist();
    assertThat(Files.readString(blocker)).isEqualTo("unrelated-user-file");
  }

  private Fixture fixture(ScaffoldTemplate template, ScaffoldCompiler compiler) {
    var clock = Clock.systemUTC();
    var redactor = new ResponseRedactor(Set.of("canary-secret"));
    var approvals = new ApprovalService(clock);
    var allow =
        new PolicyRule(
            "allow",
            PolicyRule.Effect.ALLOW,
            Set.of("*"),
            Set.of("*"),
            Set.of("*"),
            Set.of("*"),
            Set.of("*"),
            Set.of("*"),
            Set.of("*"),
            false);
    var enforcement =
        new McpEnforcementService(
            new AuthorizationPolicyEngine(List.of(allow)),
            approvals,
            new InMemoryAuditLog(clock, redactor),
            redactor);
    return new Fixture(
        new ScaffoldWorkflow(
            temporary,
            new ScaffoldTemplateRegistry(List.of(template)),
            enforcement,
            approvals,
            compiler,
            clock),
        approvals);
  }

  private static ScaffoldTemplate template(boolean dependency) {
    var java =
        new ScaffoldAsset(
            Path.of("src/main/java/example/Generated.java"),
            "package example;\n\npublic final class Generated {}\n",
            ScaffoldAsset.Kind.PROJECT);
    return new ScaffoldTemplate(
        "java-project",
        "1.0",
        dependency
            ? List.of(
                java,
                new ScaffoldAsset(
                    Path.of("pom.xml"), "<project/>\n", ScaffoldAsset.Kind.DEPENDENCY))
            : List.of(java));
  }

  private static ScaffoldRequest request(
      Path workspace, String destination, String write, String dependency) {
    return new ScaffoldRequest(
        "request",
        "project",
        "local",
        workspace,
        Path.of(destination),
        "java-project",
        "1.0",
        write,
        dependency,
        REQUESTER,
        Transport.INTERNAL);
  }

  private static ScaffoldCompiler.CompilationResult compileJava(Path workspace) {
    var source = workspace.resolve("src/main/java/example/Generated.java");
    var output = workspace.resolve("target/classes");
    try {
      Files.createDirectories(output);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
    int exit =
        javax.tools.ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", output.toString(), source.toString());
    return new ScaffoldCompiler.CompilationResult(
        exit == 0, exit == 0 ? "compiled" : "compile failed");
  }

  private static ScaffoldRequest approved(
      Fixture fixture, ScaffoldRequest request, boolean dependency) {
    var write = fixture.workflow.requestWriteApproval(request, Duration.ofMinutes(5));
    fixture.approvals.decide(write.id(), APPROVER, true);
    String dependencyId = null;
    if (dependency) {
      var approval = fixture.workflow.requestDependencyApproval(request, Duration.ofMinutes(5));
      fixture.approvals.decide(approval.id(), APPROVER, true);
      dependencyId = approval.id();
    }
    return request(request.workspace(), request.destination().toString(), write.id(), dependencyId);
  }

  private static ScaffoldResult result(java.util.concurrent.Future<ScaffoldResult> future) {
    try {
      return future.get();
    } catch (Exception failure) {
      throw new AssertionError(failure);
    }
  }

  private record Fixture(ScaffoldWorkflow workflow, ApprovalService approvals) {}
}
