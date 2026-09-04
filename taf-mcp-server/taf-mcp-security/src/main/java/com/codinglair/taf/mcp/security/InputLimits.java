package com.codinglair.taf.mcp.security;

public record InputLimits(long maximumBytes, int maximumDepth, int maximumElements) {
    public static final InputLimits DEFAULT = new InputLimits(1_048_576, 32, 10_000);

    public InputLimits {
        if (maximumBytes < 1 || maximumDepth < 1 || maximumElements < 1) {
            throw new IllegalArgumentException("input limits must be positive");
        }
    }
}
