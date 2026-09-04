package com.codinglair.taf.mcp.resources;

import java.util.List;
import java.util.Optional;

/** Deterministically ordered, bounded resource page with an opaque continuation cursor. */
public record ResourcePage<T>(List<T> items, Optional<String> nextCursor) {
  public ResourcePage {
    items = List.copyOf(items);
    nextCursor = nextCursor == null ? Optional.empty() : nextCursor;
  }
}
