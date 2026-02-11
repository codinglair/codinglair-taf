package com.codinglair.taf.core.data.dao.abstraction;

/**
 * Connector interface for database connections.
 */
public interface AbstractConnector {
    void connect();
    void disconnect();
}
