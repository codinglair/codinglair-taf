package com.codinglair.taf.core.test.abstraction;

import com.codinglair.taf.core.AppConfigLoader;
import com.codinglair.taf.core.annotation.data.TestCaseId;
import com.codinglair.taf.core.controller.factory.TestControllerFactory;
import com.codinglair.taf.core.data.abstraction.TestContext;
import com.codinglair.taf.core.data.factory.TestContextFactory;
import com.codinglair.taf.core.environment.EnvironmentProperties;
import com.codinglair.taf.core.reporting.factory.ReporterFactory;
import com.codinglair.taf.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.core.reporting.impl.allure.AllureListener;
import com.codinglair.taf.core.reporting.impl.allure.ReportUtility;
import com.codinglair.taf.core.utils.secret.abstraction.SecretManager;
import com.codinglair.taf.core.utils.secret.factory.SecretManagerFactory;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.assertj.core.api.SoftAssertions;
import org.testng.ITestContext;
import org.testng.annotations.*;
import com.codinglair.taf.core.controller.abstraction.TestController;

import java.lang.reflect.Method;
import java.util.Map;

@Listeners(AllureListener.class)
public abstract class BaseTest<T extends TestController, I, O> {
    protected T controller;
    protected TestReporter reporter;
    protected TestContext<?,?> context;
    protected static EnvironmentProperties envProps;
    private static SecretManager secretManager;
    protected SoftAssertions softAssert;

    // --- THREAD-SPECIFIC STATE FOR PARALLEL EXECUTIONS
    private static final ThreadLocal<TestController> controllerThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<TestReporter> reporterThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<TestContext<?, ?>> contextThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<String> currentTestCaseId = new ThreadLocal<>();
    private static final ThreadLocal<SoftAssertions> softAssertThreadLocal = new ThreadLocal<>();

    @BeforeSuite
    public void setUpSecretManager(){
        //Setting up Environment
        envProps = new EnvironmentProperties(
                AppConfigLoader.loadSubmoduleConfig("config/app.config")
                        .getProperty("ENV_PROPERTIES_PATH"));

        //Initializing SecretManager
        secretManager = SecretManagerFactory.create(AppConfigLoader.loadSubmoduleConfig("config/app.config")
                .getProperty("SECRET_MANAGER_CLASS"));

        //Initializing text context
        String contextClassName = AppConfigLoader.loadSubmoduleConfig("config/app.config")
                .getProperty("TEST_CONTEXT_CLASS");
        context = TestContextFactory.createContext(contextClassName, envProps);

        //Setting up for multi-assertion validations, to prevent a failure during any first assert
        softAssert = new SoftAssertions();
        softAssertThreadLocal.set(softAssert);
    }

    @BeforeMethod
    public void init(ITestContext testNgContext, Method method) {
        //Retrievning test id from a functional TestNG test
        String testCaseId;
        if (method.isAnnotationPresent(TestCaseId.class)) {
            testCaseId = method.getAnnotation(TestCaseId.class).value();
        } else {
            // Optional: If no annotation found
            testCaseId=method.getName();
        }
        configTest(testNgContext, testCaseId);
    }

    /**
     * Common test setup, which can run for both TestNG functional tests
     * and BDD tests.
     *
     * @param testNgContext
     * @param id
     */
    public void configTest(ITestContext testNgContext, String id) {
        String reporterClass = AppConfigLoader
                .loadSubmoduleConfig("config/app.config")
                .getProperty("REPORTER_CLASS");
        //Setting up Test Reporter
        reporter = ReporterFactory.create(reporterClass);
        reporterThreadLocal.set(reporter);

        contextThreadLocal.set(context);

        //Injecting additional environment properties from TestNG context.
        envProps.putEnvProperty("report_name", testNgContext.getName());
        //Injecting potential additional env parameters from the test suite.
        Map<String, String> allParams = testNgContext.getCurrentXmlTest().getAllParameters();
        enhanceEnvironment(allParams);

        // Initialize Controller. Parameter name "controllerClass" must be present either
        // in test suite or in app.config and it must specify the controller implementation.
        String controllerClass = getControllerClassName(allParams);
        this.controller = TestControllerFactory.create(controllerClass);
        controllerThreadLocal.set(controller);

        // Pass the fully merged properties to the controller
        controllerThreadLocal.get().setup(envProps);

        // Get test case id from test method annotation.
        setCurrentTestCaseId(id);
    }

    /**
     * Additional env properties or overrides could be placed in the test suite
     * parameters with prefix "env." and they will be added to EnvironmentProperties
     *
     * @param params
     */
    private void enhanceEnvironment(Map<String, String> params) {
        params.forEach((key, value) -> {
            if (key.startsWith("env.")) {
                String cleanKey = key.substring(4); // Remove "env." prefix
                envProps.putEnvProperty(cleanKey, value);
            }
        });
    }

    /**
     * Attempts retrieving Controller impl class name either from TestNG suite or
     * the app.config. Otherwise, throws IllegalStateException.
     *
     * @param testNGParams
     * @return
     */
    private String getControllerClassName(Map<String, String> testNGParams) {
        return java.util.Optional.ofNullable(testNGParams.get("controllerClass"))
                .filter(str -> !str.isBlank())
                .or(() -> java.util.Optional.ofNullable(
                        AppConfigLoader.loadSubmoduleConfig("config/app.config")
                                .getProperty("controllerClass")))
                .filter(str -> !str.isBlank())
                .orElseThrow(() -> new IllegalStateException("Controller class name could not be initialized from TestNG parameters or app.config."));
    }

    public T getController() {
        return (T) controllerThreadLocal.get();
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

    public static TestReporter getReporter() {
        return reporterThreadLocal.get();
    }

    @SuppressWarnings("unchecked")
    public static TestContext<?, ?> getContext() { return contextThreadLocal.get(); }

    public static String getActiveTestCaseId() {
        return currentTestCaseId.get();
    }

    private void setCurrentTestCaseId(String testCaseId) {
        currentTestCaseId.set(testCaseId);
    }

    /**
     * Clears all ThreadLocal variables to prevent memory leaks
     * and state bleeding between parallel tests/scenarios.
     */
    protected void stopTest() {
        // 1. Clear the Identity
        if (currentTestCaseId != null) {
            currentTestCaseId.remove();
        }

        // 2. Clear the Reporter
        if (reporterThreadLocal != null) {
            reporterThreadLocal.remove();
        }

        // 3. Clear the Context
        if (contextThreadLocal != null) {
            contextThreadLocal.remove();
        }

        // 4. Clear the Controller/Actor
        if (controllerThreadLocal != null) {
            controllerThreadLocal.remove();
        }

        // 5. Clear the Soft Assertions
        if (softAssertThreadLocal != null) {
            softAssertThreadLocal.remove();
        }
    }

    private void concludeTest() {
        try {
            if (softAssertThreadLocal.get() != null) {
                softAssertThreadLocal.get().assertAll();
            }
        } finally {
            if (getController() != null) {
                getController().teardown();
            }
            stopTest(); // Remove ThreadLocals
        }
    }

    @AfterMethod
    public void teardown() {
        concludeTest();
    }

    @AfterSuite(alwaysRun = true)
    public void tearDownSuite() {
        ReportUtility.generateAllureReport(envProps);
    }

    //BDD Cucumber Hooks
    @Before(order = 0)
    public void setup(Scenario scenario) {
        // Retrieve the TestNG context dynamically
        ITestContext testNgContext = org.testng.Reporter.getCurrentTestResult().getTestContext();

        // Resolve the test case ID (Expected format "@TestCaseId:TC-12345" tag
        // If tag is missing, it's going to fallback to Scenario Name)
        String testCaseId = scenario.getSourceTagNames().stream()
                .filter(tag -> tag.toLowerCase().startsWith("@testcaseid:"))
                .map(tag -> tag.split(":")[1].trim())
                .findFirst()
                .orElse(scenario.getName());

        configTest(testNgContext, testCaseId);
    }

    @After(order = 0)
    public void teardown(Scenario scenario) {
        concludeTest();
    }
}
