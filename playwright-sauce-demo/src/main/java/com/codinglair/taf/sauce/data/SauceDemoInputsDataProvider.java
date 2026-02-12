package com.codinglair.taf.sauce.data;

import com.codinglair.taf.core.data.impl.CsvFileDataProvider;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public class SauceDemoInputsDataProvider extends CsvFileDataProvider<WebUser> {
    public SauceDemoInputsDataProvider() {
        super("inputs.csv", WebUser.class, '|');
    }

    @Override
    public WebUser read(String testCaseId) {
        return payload.get(testCaseId).getFirst();
    }

    @Override
    public WebUser read(String testCaseId, String correlationId) {
        List<WebUser> inputs = payload.get(testCaseId);
        return inputs.stream()
                .filter(Objects::nonNull)
                .filter(input -> input.getUserName().contains(correlationId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Check the data. No input found matching: " + correlationId));
    }
}
