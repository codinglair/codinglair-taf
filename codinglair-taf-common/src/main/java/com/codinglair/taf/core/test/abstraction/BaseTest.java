package com.codinglair.taf.core.test.abstraction;

import com.codinglair.taf.core.AppConfigLoader;
import com.codinglair.taf.core.annotation.data.TestCaseId;
import com.codinglair.taf.core.data.abstraction.TestContext;
import com.codinglair.taf.core.environment.EnvironmentProperties;
import com.codinglair.taf.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.core.reporting.impl.allure.AllureListener;
import com.codinglair.taf.core.reporting.impl.allure.ReportUtility;
import com.codinglair.taf.core.test.TestLifecycleContainer;
import com.codinglair.taf.core.test.TestLifecycleManager;
import com.codinglair.taf.core.utils.secret.abstraction.SecretManager;
import com.codinglair.taf.core.utils.secret.factory.SecretManagerFactory;
import org.testng.ITestContext;
import org.testng.annotations.*;
import com.codinglair.taf.core.controller.abstraction.TestController;

import java.lang.reflect.Method;

@Listeners(AllureListener.class)
public abstract class BaseTest<T extends TestController, I, O> {
    private final TestLifecycleManager testLifecycleMgr = new TestLifecycleManager();
    private static SecretManager secretManager;
    protected static EnvironmentProperties envProps;

    @BeforeSuite(alwaysRun = true)
    public void setUpSecretManager(){
        testLifecycleMgr.configSuite();

        envProps = testLifecycleMgr.getEnvProps();

        //Initializing SecretManager
        secretManager = SecretManagerFactory.create(AppConfigLoader.loadSubmoduleConfig("config/app.config")
                .getProperty("SECRET_MANAGER_CLASS"));

    }

    @BeforeMethod
    public void init(ITestContext testNgContext, Method method) {
        if (this.getClass().isAnnotationPresent(io.cucumber.testng.CucumberOptions.class)) {
            // Skip TestNG init: Cucumber @Before hooks will handle LifecycleManager
            return;
        }
        TestLifecycleContainer.setManager(testLifecycleMgr);
        //Retrieving test id from a functional TestNG test
        String testCaseId;
        if (method.isAnnotationPresent(TestCaseId.class)) {
            testCaseId = method.getAnnotation(TestCaseId.class).value();
        } else {
            // Optional: If no annotation found
            testCaseId=method.getName();
        }
        TestLifecycleContainer.getManager().configTest(testNgContext, testCaseId);

    }

    public T getController() {
        return TestLifecycleContainer.getManager().getController();
    }


    //The reason for lazy initialization is for possible invocation outside TestNG suites.
    public static SecretManager getSecretManager() {
        if (secretManager == null) {
            synchronized (BaseTest.class) { // Thread-safety for parallel runs
                if (secretManager == null) {
                    String managerClass = AppConfigLoader.loadSubmoduleConfig("config/app.config")
                            .getProperty("SECRET_MANAGER_CLASS");
                    secretManager = SecretManagerFactory.create(managerClass);
                }
            }
        }
        return secretManager;
    }

    public TestReporter getReporter() {
        return TestLifecycleContainer.getManager().getReporter();
    }

    @SuppressWarnings("unchecked")
    public TestContext<?, ?> getContext() {
        return TestLifecycleContainer.getManager().getContext();
    }

    public String getActiveTestCaseId() {
        return TestLifecycleContainer.getManager().getActiveTestCaseId();
    }

    public EnvironmentProperties getEnvProps() {
        return TestLifecycleContainer.getManager().getEnvProps();
    }

    /**
     * Clears all ThreadLocal variables to prevent memory leaks
     * and state bleeding between parallel tests/scenarios.
     */
    protected void stopTest() {
        TestLifecycleContainer.clear();
    }

    private void concludeTest() {
        try {
            if (TestLifecycleContainer.getManager().getSoftAssert() != null) {
                TestLifecycleContainer.getManager().getSoftAssert().assertAll();
            }
        } finally {
            if (TestLifecycleContainer.getManager().getController() != null) {
                TestLifecycleContainer.getManager().getController().teardown();
            }
            stopTest(); // Remove ThreadLocals
        }
    }

    @AfterMethod
    public void teardown() {
        if (this.getClass().isAnnotationPresent(io.cucumber.testng.CucumberOptions.class)) {
            // Skip TestNG init: Cucumber @After hooks will handle LifecycleManager
            return;
        }
        concludeTest();
    }

    @AfterSuite(alwaysRun = true)
    public void tearDownSuite() {
        ReportUtility.generateAllureReport(envProps);
    }
}
