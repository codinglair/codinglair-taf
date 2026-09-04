package com.codinglair.taf.mcp.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ApprovalService {
    private final ConcurrentHashMap<String, ApprovalRequest> requests = new ConcurrentHashMap<>();
    private final Clock clock;

    public ApprovalService(Clock clock) {
        this.clock = clock;
    }

    public ApprovalRequest request(AuthorizationContext context, String operationDigest, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("approval ttl must be positive");
        }
        var now = clock.instant();
        var id = UUID.randomUUID().toString();
        var request =
                new ApprovalRequest(
                        id,
                        bindingDigest(context, operationDigest),
                        context.identity().userId(),
                        ApprovalStatus.PENDING,
                        now.plus(ttl),
                        List.of(new ApprovalEvent(0, ApprovalStatus.PENDING, context.identity().userId(), now)));
        requests.put(id, request);
        return request;
    }

    public ApprovalRequest decide(String id, CallerIdentity approver, boolean approved) {
        if (approver.kind() != IdentityKind.HUMAN) {
            throw new IllegalArgumentException("only a human identity may decide an approval");
        }
        return requests.compute(
                id,
                (_, current) -> {
                    if (current == null) {
                        throw new IllegalArgumentException("approval request not found");
                    }
                    if (current.status() != ApprovalStatus.PENDING) {
                        throw new IllegalStateException("approval decision is immutable");
                    }
                    if (current.requesterId().equals(approver.userId())) {
                        throw new IllegalArgumentException("requester cannot decide their own approval");
                    }
                    var status = approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
                    var events = new java.util.ArrayList<>(current.history());
                    events.add(new ApprovalEvent(events.size(), status, approver.userId(), clock.instant()));
                    return new ApprovalRequest(
                            current.id(),
                            current.bindingDigest(),
                            current.requesterId(),
                            status,
                            current.expiresAt(),
                            events);
                });
    }

    public boolean permits(String id, AuthorizationContext context, String operationDigest) {
        var request = requests.get(id);
        return request != null
                && request.status() == ApprovalStatus.APPROVED
                && clock.instant().isBefore(request.expiresAt())
                && MessageDigest.isEqual(
                        request.bindingDigest().getBytes(StandardCharsets.US_ASCII),
                        bindingDigest(context, operationDigest).getBytes(StandardCharsets.US_ASCII));
    }

    public Optional<ApprovalRequest> find(String id) {
        return Optional.ofNullable(requests.get(id));
    }

    private static String bindingDigest(AuthorizationContext context, String operationDigest) {
        if (operationDigest == null || operationDigest.isBlank()) {
            throw new IllegalArgumentException("operation digest is required");
        }
        var canonical =
                String.join(
                        "\u001f",
                        context.identity().userId(),
                        context.identity().agentId(),
                        context.project(),
                        context.environment(),
                        context.action(),
                        context.permission(),
                        operationDigest);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
