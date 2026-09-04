package com.codinglair.taf.mcp.security;

import java.util.Map;
import java.util.concurrent.Callable;

public final class McpEnforcementService {
    private final AuthorizationPolicyEngine policyEngine;
    private final ApprovalService approvals;
    private final AuditLog audit;
    private final ResponseRedactor redactor;
    private final InputSizeValidator inputValidator;

    public McpEnforcementService(
            AuthorizationPolicyEngine policyEngine,
            ApprovalService approvals,
            AuditLog audit,
            ResponseRedactor redactor) {
        this(policyEngine, approvals, audit, redactor, new InputSizeValidator(InputLimits.DEFAULT));
    }

    public McpEnforcementService(
            AuthorizationPolicyEngine policyEngine,
            ApprovalService approvals,
            AuditLog audit,
            ResponseRedactor redactor,
            InputSizeValidator inputValidator) {
        this.policyEngine = policyEngine;
        this.approvals = approvals;
        this.audit = audit;
        this.redactor = redactor;
        this.inputValidator = inputValidator;
    }

    public EnforcementResult enforce(EnforcementRequest request, Callable<?> sideEffect) {
        var context = request.authorization();
        try {
            inputValidator.validate(request.input());
        } catch (IllegalArgumentException exception) {
            audit.append(
                    request.correlationId(),
                    "input.rejected",
                    context.identity().userId(),
                    Map.of("reason", exception.getMessage()));
            return new EnforcementResult(
                    EnforcementResult.Status.INPUT_REJECTED,
                    Map.of("error", "input exceeds configured limits"));
        }
        audit.append(
                request.correlationId(),
                "tool.invoked",
                context.identity().userId(),
                Map.of(
                        "transport", request.transport().name(),
                        "action", context.action(),
                        "project", context.project(),
                        "environment", context.environment(),
                        "agentId", context.identity().agentId()));
        var decision = policyEngine.decide(context);
        audit.append(
                request.correlationId(),
                "authorization.decided",
                context.identity().userId(),
                Map.of("allowed", decision.allowed(), "rules", decision.ruleIds()));
        if (!decision.allowed()) {
            return new EnforcementResult(EnforcementResult.Status.DENIED, Map.of("error", "denied"));
        }
        if (decision.approvalRequired()
                && (request.approvalId() == null
                        || !approvals.permits(
                                request.approvalId(), context, request.operationDigest()))) {
            audit.append(
                    request.correlationId(),
                    "approval.required",
                    context.identity().userId(),
                    Map.of("action", context.action()));
            return new EnforcementResult(
                    EnforcementResult.Status.APPROVAL_REQUIRED,
                    Map.of("error", "valid approval required"));
        }
        try {
            var sanitized = redactor.redact(sideEffect.call());
            audit.append(
                    request.correlationId(),
                    "tool.completed",
                    context.identity().userId(),
                    Map.of("result", sanitized == null ? "completed" : sanitized));
            return new EnforcementResult(EnforcementResult.Status.ALLOWED, sanitized);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            audit.append(
                    request.correlationId(),
                    "tool.failed",
                    context.identity().userId(),
                    Map.of("failure", "interrupted"));
            return new EnforcementResult(
                    EnforcementResult.Status.FAILED, Map.of("error", "operation interrupted"));
        } catch (Exception exception) {
            audit.append(
                    request.correlationId(),
                    "tool.failed",
                    context.identity().userId(),
                    Map.of("failure", exception.getClass().getSimpleName()));
            return new EnforcementResult(
                    EnforcementResult.Status.FAILED, Map.of("error", "operation failed"));
        }
    }

    public void auditCredentialResolution(
            String correlationId, CallerIdentity identity, String provider, String reference) {
        audit.append(
                correlationId,
                "credential.resolved",
                identity.userId(),
                Map.of("provider", provider, "reference", reference));
    }

    /** Applies the enforcement boundary's configured redaction policy to response content. */
    public Object redact(Object value) {
        return redactor.redact(value);
    }
}
