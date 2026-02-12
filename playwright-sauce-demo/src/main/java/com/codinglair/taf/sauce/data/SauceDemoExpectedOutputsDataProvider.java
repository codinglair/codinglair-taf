package com.codinglair.taf.sauce.data;

import com.codinglair.taf.core.data.impl.CsvFileDataProvider;

import java.util.NoSuchElementException;
import java.util.Objects;

public class SauceDemoExpectedOutputsDataProvider extends CsvFileDataProvider<ProductPojo> {

    public SauceDemoExpectedOutputsDataProvider() {
        super("expected_outputs.csv", ProductPojo.class, '|');
    }

    @Override
    public ProductPojo read(String testCaseId) {
        return payload.get(testCaseId).getFirst();
    }

    @Override
    public ProductPojo read(String testCaseId, String correlationId) {
        return payload.get(testCaseId)
                .stream()
                .filter(Objects::nonNull)
                .filter(output -> output.getProductName().contains(correlationId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Check the data. No input found matching: " + correlationId));
    }
}
