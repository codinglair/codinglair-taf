package com.codinglair.taf.mcp.security;

public final class InputLimitExceededException extends IllegalArgumentException {
    public InputLimitExceededException(String limit) {
        super("MCP input exceeds the configured " + limit + " limit");
    }
}
