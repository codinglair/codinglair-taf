package com.codinglair.taf.database;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface JdbcConnectionFactory {
  Connection open(SutConnectionDescriptor descriptor, String sessionId) throws SQLException;
}
