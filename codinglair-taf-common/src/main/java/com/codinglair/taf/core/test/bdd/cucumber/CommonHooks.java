package com.codinglair.taf.core.test.bdd.cucumber;

import com.codinglair.taf.core.test.TestLifecycleContainer;
import com.codinglair.taf.core.test.TestLifecycleManager;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.testng.ITestContext;

public class CommonHooks {
    private TestLifecycleManager testLifecycleMgr = new TestLifecycleManager();
    //BDD Cucumber Hooks
    @Before(order = 0)
    public void setup(Scenario scenario) {
        // Retrieve the TestNG context dynamically
        ITestContext testNgContext = org.testng.Reporter.getCurrentTestResult().getTestContext();

        // Resolve the test case ID (Expected format "@TestCaseId:TC-12345" tag
        // If tag is missing, it's going to fall back to Scenario Name)
        String testCaseId = scenario.getSourceTagNames().stream()
                .filter(tag -> tag.toLowerCase().startsWith("@testcaseid:"))
                .map(tag -> tag.split(":")[1].trim())
                .findFirst()
                .orElse(scenario.getName());
        TestLifecycleContainer.setManager(testLifecycleMgr);
        TestLifecycleContainer.getManager().configSuite();
        TestLifecycleContainer.getManager().configTest(testNgContext, testCaseId);

    }

    @After(order = 0)
    public void teardown(Scenario scenario) {
        try {
            if (TestLifecycleContainer.getManager().getSoftAssert() != null) {
                TestLifecycleContainer.getManager().getSoftAssert().assertAll();
            }
        } finally {
            if (TestLifecycleContainer.getManager().getController() != null) {
                TestLifecycleContainer.getManager().getController().teardown();
            }
            TestLifecycleContainer.clear();
        }
    }
}
