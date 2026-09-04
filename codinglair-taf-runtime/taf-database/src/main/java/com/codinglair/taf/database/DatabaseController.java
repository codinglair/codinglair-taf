package com.codinglair.taf.database;

import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import java.sql.Connection;
import java.util.function.Function;

public interface DatabaseController extends TestController {
  @ControllerAction("Query SUT database")
  QueryResult query(String sql, Object... parameters);

  @ControllerAction("Update SUT database")
  int update(String sql, Object... parameters);

  @ControllerAction("Execute SUT database transaction")
  <T> T transaction(Function<JdbcTransaction, T> work);

  @ControllerAction("Set up SUT database data")
  int setup(String sql, Object... parameters);

  @ControllerAction("Clean up SUT database data")
  int cleanup(String sql, Object... parameters);

  Connection nativeConnection();
}
