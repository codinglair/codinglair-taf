package com.codinglair.taf.mcp.prompts;

/** One bounded page of textual resource content or a reference for non-text content. */
public record ResourceChunk(
    String schemaVersion,
    String uri,
    String mediaType,
    int sizeBytes,
    String content,
    boolean redacted,
    String nextCursor,
    boolean referenceOnly) {}
