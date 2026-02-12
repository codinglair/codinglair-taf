package com.codinglair.taf.sauce.data;

import com.codinglair.taf.core.data.abstraction.TestContext;
import com.codinglair.taf.core.environment.EnvironmentProperties;

public class SauceDemoContext extends TestContext<WebUser, ProductPojo> {
    public SauceDemoContext(EnvironmentProperties environmentProperties) {
        super(environmentProperties,
                new SauceDemoInputsDataProvider(),
                new SauceDemoExpectedOutputsDataProvider());
    }

}
