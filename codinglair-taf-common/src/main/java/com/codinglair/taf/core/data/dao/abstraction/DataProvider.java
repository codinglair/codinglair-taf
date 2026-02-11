package com.codinglair.taf.core.data.dao.abstraction;

import java.util.List;

/**
 * An interface for a data access object.
 * Use it to implement a retrieval of the test data from a storage.
 */
public interface DataProvider<KEY, OBJECT> {
    OBJECT read(KEY key);
    OBJECT read(KEY key, String correlationId);
    List<OBJECT> readAll(KEY key);
    void initialize();
}
