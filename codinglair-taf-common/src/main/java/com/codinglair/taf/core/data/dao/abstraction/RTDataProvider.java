package com.codinglair.taf.core.data.dao.abstraction;

public abstract class RTDataProvider<KEY, OBJECT> implements DataProvider<KEY, OBJECT> {
    protected AbstractConnector connector;

    protected RTDataProvider(AbstractConnector connector) {
        this.connector = connector;
    }
}
