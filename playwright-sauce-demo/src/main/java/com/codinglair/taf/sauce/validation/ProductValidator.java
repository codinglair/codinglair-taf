package com.codinglair.taf.sauce.validation;

import com.codinglair.taf.core.annotation.reporting.TafStep;
import com.codinglair.taf.core.validation.abstraction.Validator;
import com.codinglair.taf.sauce.data.ProductPojo;
import org.assertj.core.api.Assertions;

public class ProductValidator implements Validator<ProductPojo> {

    /**
     * Utilizes AssertJ's recursive comparison for a deep-dive check
     * It automatically handles nulls and provides a perfect diff
     *
     * @param expected
     * @param actual
     */
    @Override
    @TafStep("Validate Product Details: Expected {0} vs Actual {1}")
    public void validate(ProductPojo expected, ProductPojo actual) {
        Assertions.assertThat(actual)
                .as("Product verification for: " + expected.getProductName())
                .isNotNull()
                .usingRecursiveComparison()
                // We can ignore specific fields if needed (e.g. dynamic IDs)
                .ignoringFields("testCaseId")
                .isEqualTo(expected);
    }
}
