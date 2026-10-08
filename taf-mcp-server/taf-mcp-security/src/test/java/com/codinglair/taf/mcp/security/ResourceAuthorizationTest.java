package com.codinglair.taf.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.security.ResourceAccess;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Caller-bound resource policy supplements action enforcement")
class ResourceAuthorizationTest {
  final CallerIdentity caller =
      new CallerIdentity("worker-user", Set.of("tester"), "agent", IdentityKind.AGENT);
  final AuthorizationContext context =
      new AuthorizationContext(caller, "project", "test", "execute", "mobile");
  final ResourceAccess endpoint =
      new ResourceAccess(ResourceAccess.Kind.ENDPOINT, "https://appium.example/custom", "connect");

  PolicyRule rule(String id, PolicyRule.Effect effect, boolean approval) {
    return new PolicyRule(
        id,
        effect,
        Set.of("worker-user"),
        Set.of("tester"),
        Set.of("project"),
        Set.of("test"),
        Set.of("execute"),
        Set.of("agent"),
        Set.of("mobile"),
        approval);
  }

  @Test
  @DisplayName("action-only grants deny resources and resource-only grants cannot bypass actions")
  void bothBoundaries() {
    var allow = rule("allow", PolicyRule.Effect.ALLOW, false);
    assertThat(
            new AuthorizationPolicyEngine(List.of(allow))
                .decideResource(context, endpoint)
                .allowed())
        .isFalse();
    var resource = new ResourcePolicyRule(allow, Set.of(endpoint));
    assertThat(
            new AuthorizationPolicyEngine(List.of(), Set.of(), List.of(resource))
                .decideResource(context, endpoint)
                .allowed())
        .isFalse();
    var engine = new AuthorizationPolicyEngine(List.of(allow), Set.of(), List.of(resource));
    assertThat(engine.decideResource(context, endpoint).allowed()).isTrue();
    assertThat(
            engine
                .decideResource(
                    context,
                    new ResourceAccess(
                        ResourceAccess.Kind.ENDPOINT, "https://job-override.example", "connect"))
                .allowed())
        .isFalse();
    var wrongCaller =
        new AuthorizationContext(
            new CallerIdentity("other", Set.of("tester"), "agent", IdentityKind.AGENT),
            "project",
            "test",
            "execute",
            "mobile");
    assertThat(engine.decideResource(wrongCaller, endpoint).allowed()).isFalse();
  }

  @Test
  @DisplayName("explicit resource denials win and approved action context remains required")
  void denialAndApproval() {
    var allow = rule("allow", PolicyRule.Effect.ALLOW, true);
    var resource = new ResourcePolicyRule(allow, Set.of(endpoint));
    var denied =
        new ResourcePolicyRule(rule("deny", PolicyRule.Effect.DENY, false), Set.of(endpoint));
    assertThat(
            new AuthorizationPolicyEngine(List.of(allow), Set.of(), List.of(resource, denied))
                .decideResource(context, endpoint)
                .allowed())
        .isFalse();
    var approvals = new ApprovalService(Clock.systemUTC());
    var service =
        new McpEnforcementService(
            new AuthorizationPolicyEngine(List.of(allow), Set.of(), List.of(resource)),
            approvals,
            new InMemoryAuditLog(Clock.systemUTC(), new ResponseRedactor(Set.of("FAKE_CANARY"))),
            new ResponseRedactor(Set.of("FAKE_CANARY")));
    var request = new EnforcementRequest("correlation", Transport.STDIO, context, "digest", null);
    assertThat(service.resourceAuthorizer(request).permits(endpoint)).isFalse();
    var pending = approvals.request(context, "digest", Duration.ofMinutes(1));
    approvals.decide(
        pending.id(),
        new CallerIdentity("approver", Set.of("admin"), "human", IdentityKind.HUMAN),
        true);
    assertThat(
            service
                .resourceAuthorizer(
                    new EnforcementRequest(
                        "correlation", Transport.STDIO, context, "digest", pending.id()))
                .permits(endpoint))
        .isTrue();
    assertThat(
            service
                .resourceAuthorizer(
                    new EnforcementRequest(
                        "correlation", Transport.STDIO, context, "changed-job", pending.id()))
                .permits(endpoint))
        .isFalse();
  }
}
