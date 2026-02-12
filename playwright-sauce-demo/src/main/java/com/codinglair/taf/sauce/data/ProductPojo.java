package com.codinglair.taf.sauce.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.codinglair.taf.core.data.abstraction.IdentifiableTestData;

public class ProductPojo implements IdentifiableTestData {
    @JsonProperty("TestCaseId")
    private String testCaseId;
    @JsonProperty("ProductName")
    private String productName;
    @JsonProperty("ProductDescription")
    private String productDescription;
    @JsonProperty("Price")
    private String price;

    public String getProductName() {
        return productName;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public String getPrice(){
        return price;
    }

    public void setProductName(String productName) {
        this.productName=productName;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    @Override
    public String getTestCaseId() {
        return testCaseId;
    }

    @Override
    public String toString() {
        // This is what will show up in the @TafStep description
        return String.format("[Product name: %s | Product desc: %s | Price: %s]",
                productName, productDescription, price);
    }
}
