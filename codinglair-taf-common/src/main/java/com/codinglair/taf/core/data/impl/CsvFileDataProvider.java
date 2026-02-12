package com.codinglair.taf.core.data.impl;

import com.codinglair.taf.core.data.abstraction.IdentifiableTestData;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.codinglair.taf.core.data.dao.abstraction.BulkDataProvider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public abstract class CsvFileDataProvider<V extends IdentifiableTestData> extends BulkDataProvider<String, V> {
    private final String fileName;
    private final Class<V> valueType;
    private final char delimiter;

    public CsvFileDataProvider(String fileName, Class<V> valueType, char delimiter) {
        super(new HashMap<>());
        this.fileName = fileName;
        this.valueType = valueType;
        this.delimiter = delimiter;
        initialize();
    }

    @Override
    public void initialize() {
        CsvMapper mapper = new CsvMapper();
        // Determine schema from the POJO class and set the submodule's delimiter
        CsvSchema schema = mapper.schemaFor(valueType)
                .withHeader()
                .withColumnSeparator(delimiter)
                .withColumnReordering(true);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("data/" + fileName)) {
            if (is == null) throw new FileNotFoundException("Data file not found: " + fileName);

            // Directly map rows to POJO instances
            MappingIterator<V> it = mapper.readerFor(valueType).with(schema).readValues(is);
            List<V> allRows = it.readAll();

            // Group by the key provided by the Marker Interface
            payload = allRows.stream()
                    .collect(Collectors.groupingBy(IdentifiableTestData::getTestCaseId));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}