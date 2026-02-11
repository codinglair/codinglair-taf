package com.codinglair.taf.core.ui.abstraction;

import com.codinglair.taf.core.controller.abstraction.TestController;

public abstract class BasePage<T extends TestController> {
    protected T testController;
    public BasePage(T testController){
        this.testController = testController;
    }
}
