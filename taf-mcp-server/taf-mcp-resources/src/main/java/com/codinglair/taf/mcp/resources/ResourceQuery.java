package com.codinglair.taf.mcp.resources;

/** Common bounded resource-list query. */
public record ResourceQuery(String filter, int pageSize, String cursor) {
  public static final int MAXIMUM_PAGE_SIZE = 100;

  public ResourceQuery {
    filter = filter == null ? "" : filter.strip();
    if (filter.length() > 128) {
      throw new IllegalArgumentException("filter exceeds 128 characters");
    }
    if (pageSize < 1 || pageSize > MAXIMUM_PAGE_SIZE) {
      throw new IllegalArgumentException("pageSize must be between 1 and 100");
    }
    cursor = cursor == null || cursor.isBlank() ? null : cursor;
  }
}
