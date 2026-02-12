package com.codinglair.taf.sauce.service;

import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.environment.EnvironmentProperties;
import com.codinglair.taf.sauce.page.SauceDemoLoginPage;
import com.codinglair.taf.sauce.data.WebUser;

public class LoginService {
    public static void login(PlaywrightController controller,
                             EnvironmentProperties envProps,
                             WebUser user) {
        controller.navigate(envProps.getEnvProperty("base_url"));
        SauceDemoLoginPage loginPage= new SauceDemoLoginPage(controller);
        loginPage.typeUserName(user.getUserName());
        loginPage.typeUserPwd(user.getPwd());
        loginPage.clickLoginBtn();
    }
}
