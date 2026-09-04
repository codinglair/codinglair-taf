package com.codinglair.taf.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("MCP shared security enforcement")
class McpSecurityTest {
    private static final String CANARY = "canary-value-9f7e";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC);
    private static final CallerIdentity CALLER =
            new CallerIdentity("user-1", Set.of("developer"), "agent-1", IdentityKind.AGENT);
    private static final CallerIdentity APPROVER =
            new CallerIdentity("approver-1", Set.of("approver"), "human-ui", IdentityKind.HUMAN);

    @Nested
    @DisplayName("Authorization policy")
    class AuthorizationPolicy {
        @Test
        @DisplayName("denies when no rule applies")
        void defaultsToDeny() {
            var decision = new AuthorizationPolicyEngine(List.of()).decide(context("inspect"));

            assertThat(decision.allowed()).isFalse();
        }

        @Test
        @DisplayName("applies the most restrictive matching rule across all dimensions")
        void denyOverridesAllow() {
            var allow = rule("role-allow", PolicyRule.Effect.ALLOW, Set.of("developer"), Set.of("*"), false);
            var environmentDeny =
                    new PolicyRule(
                            "production-deny",
                            PolicyRule.Effect.DENY,
                            Set.of("*"),
                            Set.of("*"),
                            Set.of("project-a"),
                            Set.of("production"),
                            Set.of("execute"),
                            Set.of("agent-1"),
                            Set.of("taf:test:execute"),
                            false);

            var decision =
                    new AuthorizationPolicyEngine(List.of(allow, environmentDeny))
                            .decide(context("execute"));

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.ruleIds()).containsExactly("production-deny", "role-allow");
        }

        @Test
        @DisplayName("requires approval for a consequential action even when an allow rule omits it")
        void consequentialActionDefaultsToApproval() {
            var allow = rule("allow", PolicyRule.Effect.ALLOW, Set.of("developer"), Set.of("*"), false);

            var decision = new AuthorizationPolicyEngine(List.of(allow)).decide(context("scaffold"));

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.approvalRequired()).isTrue();
        }
    }

    @Nested
    @DisplayName("Approval decisions")
    class ApprovalDecisions {
        @Test
        @DisplayName("binds approval to user agent project environment action and operation")
        void bindsApprovalToFullContext() {
            var approvals = new ApprovalService(CLOCK);
            var approved = approvals.request(context("execute"), "digest-a", Duration.ofMinutes(5));
            approvals.decide(approved.id(), APPROVER, true);

            assertThat(approvals.permits(approved.id(), context("execute"), "digest-a")).isTrue();
            assertThat(approvals.permits(approved.id(), context("execute"), "digest-b")).isFalse();
            assertThat(approvals.permits(approved.id(), context("cancel"), "digest-a")).isFalse();
        }

        @Test
        @DisplayName("keeps decision history immutable")
        void preventsDecisionReplacement() {
            var approvals = new ApprovalService(CLOCK);
            var request = approvals.request(context("execute"), "digest", Duration.ofMinutes(5));
            var decided = approvals.decide(request.id(), APPROVER, true);

            assertThrows(
                    IllegalStateException.class,
                    () -> approvals.decide(request.id(), APPROVER, false));
            assertThat(request.history()).hasSize(1);
            assertThat(decided.history()).hasSize(2);
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> decided.history().add(decided.history().getLast()));
        }

        @Test
        @DisplayName("rejects non-human and self approval")
        void requiresSeparateHumanApprover() {
            var approvals = new ApprovalService(CLOCK);
            var request = approvals.request(context("execute"), "digest", Duration.ofMinutes(5));
            var requesterAsHuman =
                    new CallerIdentity("user-1", Set.of("approver"), "human-ui", IdentityKind.HUMAN);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> approvals.decide(request.id(), CALLER, true));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> approvals.decide(request.id(), requesterAsHuman, true));
        }

        @Test
        @DisplayName("allows exactly one terminal decision under concurrency")
        void concurrentDecisionIsSingleAndImmutable() {
            var approvals = new ApprovalService(CLOCK);
            var request = approvals.request(context("execute"), "digest", Duration.ofMinutes(5));
            var successes = new AtomicInteger();

            assertTimeoutPreemptively(
                    Duration.ofSeconds(3),
                    () -> {
                        try (var executor = Executors.newFixedThreadPool(8)) {
                            var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
                            for (var index = 0; index < 20; index++) {
                                var approve = index % 2 == 0;
                                tasks.add(
                                        () -> {
                                            try {
                                                approvals.decide(request.id(), APPROVER, approve);
                                                successes.incrementAndGet();
                                            } catch (IllegalStateException _) {
                                                // Expected for every racing decision after the first.
                                            }
                                            return null;
                                        });
                            }
                            executor.invokeAll(tasks);
                        }
                    });

            assertThat(successes).hasValue(1);
            assertThat(approvals.find(request.id()).orElseThrow().history()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Transport-neutral enforcement")
    class TransportNeutralEnforcement {
        @ParameterizedTest
        @EnumSource(Transport.class)
        @DisplayName("denies alternate transport paths before side effects")
        void denialHasNoSideEffect(Transport transport) {
            var fixture = fixture(List.of());
            var sideEffects = new AtomicInteger();

            var result =
                    fixture.service.enforce(
                            request(transport, context("scaffold"), null),
                            () -> sideEffects.incrementAndGet());

            assertThat(result.status()).isEqualTo(EnforcementResult.Status.DENIED);
            assertThat(sideEffects).hasValue(0);
        }

        @ParameterizedTest
        @EnumSource(Transport.class)
        @DisplayName("requires the same bound approval on every transport")
        void approvalCannotBeBypassed(Transport transport) {
            var fixture = fixture(List.of(rule("write", PolicyRule.Effect.ALLOW, Set.of("developer"), Set.of("*"), true)));
            var sideEffects = new AtomicInteger();

            var result =
                    fixture.service.enforce(
                            request(transport, context("scaffold"), null),
                            () -> sideEffects.incrementAndGet());

            assertThat(result.status()).isEqualTo(EnforcementResult.Status.APPROVAL_REQUIRED);
            assertThat(sideEffects).hasValue(0);
        }

        @ParameterizedTest
        @EnumSource(Transport.class)
        @DisplayName("rejects oversized input on every transport before policy or side effects")
        void oversizedInputIsRejected(Transport transport) {
            var fixture =
                    fixture(
                            List.of(
                                    rule(
                                            "read",
                                            PolicyRule.Effect.ALLOW,
                                            Set.of("developer"),
                                            Set.of("*"),
                                            false)),
                            new InputLimits(16, 4, 10));
            var sideEffects = new AtomicInteger();

            var result =
                    fixture.service.enforce(
                            request(
                                    transport,
                                    context("report"),
                                    null,
                                    Map.of("value", "this input is larger than sixteen bytes")),
                            () -> sideEffects.incrementAndGet());

            assertThat(result.status()).isEqualTo(EnforcementResult.Status.INPUT_REJECTED);
            assertThat(sideEffects).hasValue(0);
            assertThat(fixture.audit.events())
                    .singleElement()
                    .extracting(AuditEvent::type)
                    .isEqualTo("input.rejected");
        }

        @Test
        @DisplayName("rejects excessively nested input before side effects")
        void excessiveDepthIsRejected() {
            var fixture = fixture(List.of(), new InputLimits(1_000, 2, 10));
            var sideEffects = new AtomicInteger();
            var input = Map.of("level1", List.of(Map.of("level2", List.of("too-deep"))));

            var result =
                    fixture.service.enforce(
                            request(Transport.INTERNAL, context("report"), null, input),
                            () -> sideEffects.incrementAndGet());

            assertThat(result.status()).isEqualTo(EnforcementResult.Status.INPUT_REJECTED);
            assertThat(sideEffects).hasValue(0);
        }

        @Test
        @DisplayName("rejects excessive aggregate input elements before side effects")
        void excessiveElementsAreRejected() {
            var fixture = fixture(List.of(), new InputLimits(1_000, 4, 2));
            var sideEffects = new AtomicInteger();

            var result =
                    fixture.service.enforce(
                            request(
                                    Transport.INTERNAL,
                                    context("report"),
                                    null,
                                    List.of("one", "two", "three")),
                            () -> sideEffects.incrementAndGet());

            assertThat(result.status()).isEqualTo(EnforcementResult.Status.INPUT_REJECTED);
            assertThat(sideEffects).hasValue(0);
        }

        @Test
        @DisplayName("executes once after a correctly bound human approval")
        void approvedActionExecutes() {
            var fixture = fixture(List.of(rule("write", PolicyRule.Effect.ALLOW, Set.of("developer"), Set.of("*"), true)));
            var approval = fixture.approvals.request(context("scaffold"), "operation", Duration.ofMinutes(5));
            fixture.approvals.decide(approval.id(), APPROVER, true);
            var sideEffects = new AtomicInteger();

            var result =
                    fixture.service.enforce(
                            request(Transport.STDIO, context("scaffold"), approval.id()),
                            () -> Map.of("count", sideEffects.incrementAndGet()));

            assertThat(result.status()).isEqualTo(EnforcementResult.Status.ALLOWED);
            assertThat(sideEffects).hasValue(1);
        }
    }

    @Nested
    @DisplayName("Audit and redaction")
    class AuditAndRedaction {
        @Test
        @DisplayName("removes secret canaries recursively from response and audit")
        void removesCanariesFromEveryBoundary() {
            var fixture = fixture(List.of(rule("read", PolicyRule.Effect.ALLOW, Set.of("developer"), Set.of("*"), false)));

            var result =
                    fixture.service.enforce(
                            request(Transport.STREAMABLE_HTTP, context("report"), null),
                            () -> Map.of("innocent", List.of("prefix " + CANARY), "accessToken", CANARY));

            assertThat(result.body().toString()).doesNotContain(CANARY).contains(ResponseRedactor.REDACTED);
            assertThat(fixture.audit.events().toString()).doesNotContain(CANARY);
        }

        @Test
        @DisplayName("audits credential resolution metadata without resolved values")
        void auditsCredentialMetadataOnly() {
            var fixture = fixture(List.of());

            fixture.service.auditCredentialResolution("corr-1", CALLER, "vault", "credential-profile-a");

            assertThat(fixture.audit.events().getFirst().details())
                    .containsEntry("provider", "vault")
                    .containsEntry("reference", "credential-profile-a");
        }

        @Test
        @DisplayName("returns immutable append-only audit snapshots")
        void auditHistoryIsImmutable() {
            var fixture = fixture(List.of());
            fixture.service.auditCredentialResolution("corr-1", CALLER, "vault", "reference-a");
            var snapshot = fixture.audit.events();
            fixture.service.auditCredentialResolution("corr-2", CALLER, "vault", "reference-b");

            assertThat(snapshot).hasSize(1);
            assertThat(fixture.audit.events()).extracting(AuditEvent::sequence).containsExactly(0L, 1L);
            assertThrows(UnsupportedOperationException.class, () -> snapshot.add(snapshot.getFirst()));
        }

        @Test
        @DisplayName("assigns contiguous audit sequence numbers under concurrency")
        void concurrentAuditAppendsAreOrdered() {
            var fixture = fixture(List.of());

            assertTimeoutPreemptively(
                    Duration.ofSeconds(3),
                    () -> {
                        try (var executor = Executors.newFixedThreadPool(8)) {
                            var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
                            for (var index = 0; index < 50; index++) {
                                var correlation = "corr-" + index;
                                tasks.add(
                                        () -> {
                                            fixture.service.auditCredentialResolution(
                                                    correlation, CALLER, "vault", "reference");
                                            return null;
                                        });
                            }
                            executor.invokeAll(tasks);
                        }
                    });

            assertThat(fixture.audit.events())
                    .extracting(AuditEvent::sequence)
                    .containsExactlyElementsOf(java.util.stream.LongStream.range(0, 50).boxed().toList());
        }
    }

    private static Fixture fixture(List<PolicyRule> rules) {
        return fixture(rules, InputLimits.DEFAULT);
    }

    private static Fixture fixture(List<PolicyRule> rules, InputLimits inputLimits) {
        var redactor = new ResponseRedactor(Set.of(CANARY));
        var audit = new InMemoryAuditLog(CLOCK, redactor);
        var approvals = new ApprovalService(CLOCK);
        return new Fixture(
                approvals,
                audit,
                new McpEnforcementService(
                        new AuthorizationPolicyEngine(rules),
                        approvals,
                        audit,
                        redactor,
                        new InputSizeValidator(inputLimits)));
    }

    private static EnforcementRequest request(
            Transport transport, AuthorizationContext context, String approvalId) {
        return new EnforcementRequest("corr-1", transport, context, "operation", approvalId);
    }

    private static EnforcementRequest request(
            Transport transport,
            AuthorizationContext context,
            String approvalId,
            Object input) {
        return new EnforcementRequest(
                "corr-1", transport, context, "operation", approvalId, input);
    }

    private static AuthorizationContext context(String action) {
        var permission = switch (action) {
            case "execute" -> "taf:test:execute";
            case "scaffold" -> "taf:working-tree:write";
            case "cancel" -> "taf:job:cancel";
            default -> "taf:report:read";
        };
        return new AuthorizationContext(CALLER, "project-a", "production", action, permission);
    }

    private static PolicyRule rule(
            String id,
            PolicyRule.Effect effect,
            Set<String> roles,
            Set<String> environments,
            boolean approvalRequired) {
        return new PolicyRule(
                id,
                effect,
                Set.of("*"),
                roles,
                Set.of("project-a"),
                environments,
                Set.of("*"),
                Set.of("agent-1"),
                Set.of("*"),
                approvalRequired);
    }

    private record Fixture(
            ApprovalService approvals, InMemoryAuditLog audit, McpEnforcementService service) {}
}
