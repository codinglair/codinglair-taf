package com.codinglair.taf.mcp.security;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class InputSizeValidator {
    private final InputLimits limits;

    public InputSizeValidator(InputLimits limits) {
        if (limits == null) {
            throw new IllegalArgumentException("input limits are required");
        }
        this.limits = limits;
    }

    public void validate(Object input) {
        inspect(input, 0, new Counter());
    }

    private void inspect(Object value, int depth, Counter counter) {
        if (depth > limits.maximumDepth()) {
            throw new InputLimitExceededException("depth");
        }
        switch (value) {
            case null -> counter.addBytes(4);
            case CharSequence text -> counter.addBytes(utf8Length(text));
            case Number number -> counter.addBytes(utf8Length(number.toString()));
            case Boolean bool -> counter.addBytes(bool ? 4 : 5);
            case Map<?, ?> map -> {
                counter.addElements(map.size());
                for (var entry : map.entrySet()) {
                    counter.addBytes(utf8Length(String.valueOf(entry.getKey())));
                    inspect(entry.getValue(), depth + 1, counter);
                }
            }
            case Iterable<?> iterable -> {
                for (var element : iterable) {
                    counter.addElements(1);
                    inspect(element, depth + 1, counter);
                }
            }
            default -> {
                if (!value.getClass().isArray()) {
                    throw new IllegalArgumentException("MCP input contains an unsupported value type");
                }
                var length = Array.getLength(value);
                counter.addElements(length);
                for (var index = 0; index < length; index++) {
                    inspect(Array.get(value, index), depth + 1, counter);
                }
            }
        }
    }

    private static long utf8Length(CharSequence value) {
        return value.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private final class Counter {
        private long bytes;
        private int elements;

        void addBytes(long count) {
            try {
                bytes = Math.addExact(bytes, count);
            } catch (ArithmeticException _) {
                throw new InputLimitExceededException("byte-size");
            }
            if (bytes > limits.maximumBytes()) {
                throw new InputLimitExceededException("byte-size");
            }
        }

        void addElements(int count) {
            try {
                elements = Math.addExact(elements, count);
            } catch (ArithmeticException _) {
                throw new InputLimitExceededException("element-count");
            }
            if (elements > limits.maximumElements()) {
                throw new InputLimitExceededException("element-count");
            }
        }
    }
}
