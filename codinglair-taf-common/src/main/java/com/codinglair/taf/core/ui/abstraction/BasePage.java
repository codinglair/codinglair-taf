package com.codinglair.taf.core.ui.abstraction;

import com.codinglair.taf.core.controller.abstraction.TestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

public abstract class BasePage<T extends TestController> {
    protected static final Logger logger =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    protected T testController;
    public BasePage(T testController){
        this.testController = testController;
    }
}
