package com.codinglair.taf.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("MCP blueprint scaffolding integration")
class BlueprintScaffoldingServiceTest {
  private static final CallerIdentity REQUESTER =
      new CallerIdentity("requester", Set.of("developer"), "agent", IdentityKind.AGENT);
  private static final CallerIdentity APPROVER =
      new CallerIdentity("approver", Set.of("reviewer"), "human", IdentityKind.HUMAN);
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);

  @TempDir Path root;

  @Nested
  @DisplayName("validation before mutation")
  class Validation {
    @Test
    @DisplayName("rejects an incomplete provider selection without creating the destination")
    void rejectsBeforeMutation() {
      var fixture = fixture(_ -> new ScaffoldCompiler.CompilationResult(true, "passed"));
      var request = request("invalid", List.of("MESSAGING"), null, false, null, null);

      var result = fixture.service().execute(request);

      assertThat(result.status()).isEqualTo(BlueprintScaffoldResult.Status.VALIDATION_FAILED);
      assertThat(result.diagnostics())
          .extracting(BlueprintCompositionEngine.Diagnostic::correctiveAction)
          .allMatch(action -> action != null && !action.isBlank());
      assertThat(root.resolve("invalid")).doesNotExist();
    }

    @Test
    @DisplayName("rejects missing and empty capability arrays at the MCP argument boundary")
    void rejectsEmptyCapabilities() {
      var valid = new java.util.HashMap<String, Object>();
      valid.put("groupId", "com.example");
      valid.put("artifactId", "sample");
      valid.put("basePackage", "com.example.sample");
      valid.put("tafVersion", "1.1.0");

      assertThatThrownBy(() -> BlueprintRequestArguments.blueprint(valid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("capabilities must be a non-empty string array");
      valid.put("capabilities", List.of());
      assertThatThrownBy(() -> BlueprintRequestArguments.blueprint(valid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("capabilities must be a non-empty string array");
    }

    @Test
    @DisplayName("composition plans reject null write collections by contract")
    void planWritesAreNeverNull() {
      assertThatThrownBy(
              () ->
                  new BlueprintCompositionEngine.CompositionPlan(
                      null, "1.0", List.of(), List.of(), List.of(), null, List.of()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects the SUT build root before protected mutation or preflight")
    void rejectsSutRootBeforeMutation() throws Exception {
      Path sut = Files.createDirectory(root.resolve("sut"));
      Path protectedPom = sut.resolve("pom.xml");
      Files.writeString(protectedPom, "protected");
      var compiled = new java.util.concurrent.atomic.AtomicBoolean();
      var fixture =
          fixture(
              _ -> {
                compiled.set(true);
                return new ScaffoldCompiler.CompilationResult(true, "compiled");
              });
      var request = withSut(request("sut", List.of("WEB"), null, true, null, null), sut);

      assertThatThrownBy(() -> fixture.service().execute(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must not be the SUT build root");
      assertThat(compiled).isFalse();
      assertThat(Files.readString(protectedPom)).isEqualTo("protected");
    }
  }

  @Nested
  @DisplayName("approved publication and preflight")
  class Publication {
    @Test
    @DisplayName("publishes for an authorized caller without inventing an approval ceremony")
    void publishesWhenPolicyDoesNotRequireApproval() {
      var fixture = fixture(_ -> new ScaffoldCompiler.CompilationResult(true, "passed"));

      var result =
          fixture.service().execute(request("authorized", List.of("WEB"), null, true, null, null));

      assertThat(result.status()).isEqualTo(BlueprintScaffoldResult.Status.APPLIED);
      assertThat(root.resolve("authorized/pom.xml")).isRegularFile();
    }

    @Test
    @DisplayName("retains policy-driven approval for dependency and scaffold writes")
    void requiresApprovalWhenPolicyRequiresIt() {
      var fixture = fixture(_ -> new ScaffoldCompiler.CompilationResult(true, "passed"), true);

      var result =
          fixture.service().execute(request("governed", List.of("WEB"), null, true, null, null));

      assertThat(result.status()).isEqualTo(BlueprintScaffoldResult.Status.APPROVAL_REQUIRED);
      assertThat(result.summary()).isEqualTo("dependency approval required");
      assertThat(root.resolve("governed")).doesNotExist();
    }

    @Test
    @DisplayName("publishes a composite project only after both approvals and runs preflight")
    void publishesAndPreflights() throws Exception {
      var preflightWorkspace = new java.util.concurrent.atomic.AtomicReference<Path>();
      var fixture =
          fixture(
              workspace -> {
                preflightWorkspace.set(workspace);
                return new ScaffoldCompiler.CompilationResult(true, "passed");
              });
      var unapproved =
          request("composite", List.of("WEB", "API", "DATABASE"), null, true, null, null);
      String dependency =
          fixture.service().requestDependencyApproval(unapproved, Duration.ofMinutes(5));
      String write = fixture.service().requestWriteApproval(unapproved, Duration.ofMinutes(5));
      fixture.approvals().decide(dependency, APPROVER, true);
      fixture.approvals().decide(write, APPROVER, true);

      var result =
          fixture
              .service()
              .execute(
                  request(
                      "composite",
                      List.of("WEB", "API", "DATABASE"),
                      null,
                      true,
                      write,
                      dependency));

      assertThat(result.status()).isEqualTo(BlueprintScaffoldResult.Status.APPLIED);
      assertThat(result.files()).contains("pom.xml");
      assertThat(preflightWorkspace.get()).isEqualTo(root.resolve("composite"));
      assertThat(Files.readString(root.resolve("composite/pom.xml")))
          .contains(
              "codinglair-taf-starter-web",
              "codinglair-taf-starter-api",
              "codinglair-taf-starter-database")
          .doesNotContain("taf-mcp");
    }

    @Test
    @DisplayName("recognized identical projects update without replacing user files")
    void updatesRecognizedProjectDeterministically() throws Exception {
      var fixture = fixture(_ -> new ScaffoldCompiler.CompilationResult(true, "compiled"));
      var initial =
          approved(fixture, request("recognized", List.of("WEB"), null, true, null, null));
      assertThat(fixture.service().execute(initial).status())
          .isEqualTo(BlueprintScaffoldResult.Status.APPLIED);
      Files.writeString(root.resolve("recognized/user-notes.txt"), "preserve");
      var update = approved(fixture, request("recognized", List.of("WEB"), null, true, null, null));

      var result = fixture.service().execute(update);

      assertThat(result.status()).isEqualTo(BlueprintScaffoldResult.Status.APPLIED);
      assertThat(result.summary()).contains("unchanged");
      assertThat(Files.readString(root.resolve("recognized/user-notes.txt"))).isEqualTo("preserve");
    }

    @Test
    @DisplayName("protected SUT mutation fails and rolls back only owned files")
    void detectsProtectedMutationAndRollsBackOwnedFiles() throws Exception {
      Path sut = Files.createDirectory(root.resolve("sut"));
      Path protectedPom = sut.resolve("pom.xml");
      Files.writeString(protectedPom, "before");
      var fixture =
          fixture(
              _ -> {
                try {
                  Files.writeString(protectedPom, "changed-by-preflight");
                } catch (java.io.IOException failure) {
                  throw new IllegalStateException(failure);
                }
                return new ScaffoldCompiler.CompilationResult(true, "compiled");
              });
      var request = withSut(request("automation", List.of("WEB"), null, true, null, null), sut);

      var result = fixture.service().execute(approved(fixture, request));

      assertThat(result.status()).isEqualTo(BlueprintScaffoldResult.Status.FAILED);
      assertThat(result.summary()).contains("protected SUT files changed");
      assertThat(root.resolve("automation/pom.xml")).doesNotExist();
      assertThat(Files.readString(protectedPom)).isEqualTo("changed-by-preflight");
    }
  }

  private BlueprintScaffoldRequest approved(Fixture fixture, BlueprintScaffoldRequest request) {
    String dependency = fixture.service().requestDependencyApproval(request, Duration.ofMinutes(5));
    String write = fixture.service().requestWriteApproval(request, Duration.ofMinutes(5));
    fixture.approvals().decide(dependency, APPROVER, true);
    fixture.approvals().decide(write, APPROVER, true);
    return new BlueprintScaffoldRequest(
        request.requestId(),
        request.projectId(),
        request.environment(),
        request.destination(),
        request.blueprint(),
        request.publish(),
        write,
        dependency,
        request.identity(),
        request.transport(),
        request.sutRoot());
  }

  private static BlueprintScaffoldRequest withSut(BlueprintScaffoldRequest request, Path sutRoot) {
    return new BlueprintScaffoldRequest(
        request.requestId(),
        request.projectId(),
        request.environment(),
        request.destination(),
        request.blueprint(),
        request.publish(),
        request.writeApprovalId(),
        request.dependencyApprovalId(),
        request.identity(),
        request.transport(),
        sutRoot);
  }

  private Fixture fixture(ScaffoldCompiler compiler) {
    return fixture(compiler, false);
  }

  private Fixture fixture(ScaffoldCompiler compiler, boolean approvalRequired) {
    var approvals = new ApprovalService(CLOCK);
    var redactor = new ResponseRedactor(Set.of("integration-secret-canary"));
    var policy =
        new AuthorizationPolicyEngine(
            List.of(
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
                    approvalRequired)));
    var enforcement =
        new McpEnforcementService(
            policy, approvals, new InMemoryAuditLog(CLOCK, redactor), redactor);
    return new Fixture(
        new BlueprintScaffoldingService(
            root, new BlueprintCompositionEngine(), enforcement, approvals, compiler),
        approvals);
  }

  private BlueprintScaffoldRequest request(
      String destination,
      List<String> capabilities,
      String provider,
      boolean publish,
      String writeApproval,
      String dependencyApproval) {
    return new BlueprintScaffoldRequest(
        "request-1",
        "project",
        "local",
        Path.of(destination),
        BlueprintScaffoldRequest.blueprint(
            "com.example",
            "sample",
            "com.example.sample",
            "1.1.0",
            capabilities,
            provider,
            null,
            null,
            "TESTNG",
            "ALLURE",
            "FILE_CSV"),
        publish,
        writeApproval,
        dependencyApproval,
        REQUESTER,
        Transport.STDIO);
  }

  private record Fixture(BlueprintScaffoldingService service, ApprovalService approvals) {}
}
