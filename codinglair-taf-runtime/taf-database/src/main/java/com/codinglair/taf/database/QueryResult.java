package com.codinglair.taf.database;

import java.util.List;
import java.util.Map;

public record QueryResult(List<Map<String, Object>> rows) {
  public QueryResult {
    rows = rows.stream().map(Map::copyOf).toList();
  }

  public int rowCount() {
    return rows.size();
  }
}
