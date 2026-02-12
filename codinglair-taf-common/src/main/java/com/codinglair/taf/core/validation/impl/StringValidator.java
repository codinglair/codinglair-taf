package com.codinglair.taf.core.validation.impl;

import com.codinglair.taf.core.annotation.reporting.TafStep;
import com.codinglair.taf.core.validation.abstraction.Validator;

import static org.assertj.core.api.Assertions.assertThat;

public class StringValidator implements Validator<String> {
    private boolean ignoreCaseFlag = false;
    public StringValidator setIgnoreCase(boolean ignoreCaseFlag){
        this.ignoreCaseFlag = ignoreCaseFlag;
        return this;
    }

    @Override
    @TafStep("Validate that '{1}' matches expected value: '{0}'")
    public void validate(String expected, String actual) {
        if (expected == null && actual == null) {
            throw new AssertionError(String.format(
                    "Null mismatch! Expected: [%s], Actual: [%s]", expected, actual));
        };
        if(ignoreCaseFlag) {
            assertThat(actual)
                    .as("Comparing string values ignoring case")
                    .isEqualToIgnoringCase(expected);
        } else {
            assertThat(actual)
                    .as("Comparing string values")
                    .isEqualTo(expected);
        }

    }
}
