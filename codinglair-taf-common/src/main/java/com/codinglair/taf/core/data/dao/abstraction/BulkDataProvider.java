package com.codinglair.taf.core.data.dao.abstraction;

import java.util.List;
import java.util.Map;

/**
 * Use this interface when the entire data has to be loaded (usually from a file)
 *
 * @param <KEY>
 * @param <OBJECT>
 */
public abstract class BulkDataProvider<KEY, OBJECT> implements DataProvider<KEY, OBJECT> {
    protected Map<KEY, List<OBJECT>> payload;

    public BulkDataProvider(Map<KEY, List<OBJECT>> payload) {
        this.payload = payload;
    }

    @Override
    public List<OBJECT> readAll(KEY key) {
        return payload.get(key);
    }
}
