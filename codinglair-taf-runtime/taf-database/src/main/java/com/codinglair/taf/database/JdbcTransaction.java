package com.codinglair.taf.database;

import java.sql.Connection;

public interface JdbcTransaction {
  QueryResult query(String sql, Object... parameters);

  int update(String sql, Object... parameters);

  Connection nativeConnection();
}
