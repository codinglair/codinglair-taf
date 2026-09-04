package com.codinglair.taf.mcp.security;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ResponseRedactor {
    public static final String REDACTED = "[REDACTED]";
    private static final Set<String> SENSITIVE_KEY_PARTS =
            Set.of("password", "passphrase", "secret", "token", "apikey", "credential", "privatekey");

    private final List<String> canaries;

    public ResponseRedactor(Set<String> canaries) {
        if (canaries.stream().anyMatch(value -> value == null || value.isEmpty())) {
            throw new IllegalArgumentException("canaries must be non-empty");
        }
        this.canaries = List.copyOf(canaries);
    }

    public Object redact(Object value) {
        return switch (value) {
            case null -> null;
            case CharSequence text -> redactText(text.toString());
            case Map<?, ?> map -> redactMap(map);
            case Iterable<?> iterable -> redactIterable(iterable);
            default -> value.getClass().isArray() ? redactArray(value) : value;
        };
    }

    private Map<String, Object> redactMap(Map<?, ?> map) {
        var result = new LinkedHashMap<String, Object>();
        map.forEach(
                (key, value) -> {
                    var name = String.valueOf(key);
                    result.put(name, sensitive(name) ? REDACTED : redact(value));
                });
        return Map.copyOf(result);
    }

    private List<Object> redactIterable(Iterable<?> iterable) {
        var result = new ArrayList<>();
        iterable.forEach(value -> result.add(redact(value)));
        return List.copyOf(result);
    }

    private List<Object> redactArray(Object array) {
        var result = new ArrayList<>();
        for (var index = 0; index < Array.getLength(array); index++) {
            result.add(redact(Array.get(array, index)));
        }
        return List.copyOf(result);
    }

    private String redactText(String text) {
        var sanitized = text;
        for (var canary : canaries) {
            sanitized = sanitized.replace(canary, REDACTED);
        }
        return sanitized;
    }

    private static boolean sensitive(String key) {
        var normalized = key.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }
}
