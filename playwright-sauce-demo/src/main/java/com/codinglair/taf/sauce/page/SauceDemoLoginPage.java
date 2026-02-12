package com.codinglair.taf.sauce.page;

import com.codinglair.taf.core.annotation.reporting.TafStep;
import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.ui.abstraction.BasePage;

public class SauceDemoLoginPage extends BasePage<PlaywrightController> {
    private final String USER_NAME_INPUT_LOCATOR = "#user-name";
    private final String PWD_INPUT_LOCATOR = "input#password";
    private final String LOGIN_BUTTON_LOCATOR = "input#login-button";
    public SauceDemoLoginPage(PlaywrightController controller){
        super(controller);
    }

    @TafStep("Enter username")
    public void typeUserName(String userName) {
        testController.getPage().locator(USER_NAME_INPUT_LOCATOR).fill(userName);
    }

    @TafStep("Enter password")
    public void typeUserPwd(String pwd) {
        testController.getPage().locator(PWD_INPUT_LOCATOR).fill(pwd);
    }

    @TafStep("Click Login button")
    public void clickLoginBtn(){
        testController.getPage().locator(LOGIN_BUTTON_LOCATOR).click();
    }
}
