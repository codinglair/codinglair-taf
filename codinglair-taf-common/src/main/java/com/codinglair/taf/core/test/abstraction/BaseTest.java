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
    private static final ThreadLocal<TestReporter> reporterThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<TestContext<?, ?>> contextThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<String> currentTestCaseId = new ThreadLocal<>();
    private static SecretManager secretManager;
    protected SoftAssertions softAssert;

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
        softAssert = new SoftAssertions();
    }

    @BeforeMethod
    public void init(ITestContext testNgContext, Method method) {
        String reporterClass = AppConfigLoader
                .loadSubmoduleConfig("config/app.config")
                .getProperty("REPORTER_CLASS");
        //Setting up Test Reporter
        reporter = ReporterFactory.create(reporterClass);
        //Ensures safe parallel execution
        reporterThreadLocal.set(reporter);
        contextThreadLocal.set(context);

        envProps.putEnvProperty("report_name", testNgContext.getName());
        //Injecting potential additional env parameters from the test suite.
        Map<String, String> allParams = testNgContext.getCurrentXmlTest().getAllParameters();
        enhanceEnvironment(allParams);

        // Initialize Controller. Parameter name "controllerClass" must be present in test suite and specify the controller implementation.
        String controllerClass = allParams.get("controllerClass");
        this.controller = TestControllerFactory.create(controllerClass);

        // Pass the fully merged properties to the actor
        this.controller.setup(envProps);

        // Get test case id from test method annotation.
        setCurrentTestCaseId(method);
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

    public T getController() {
        return controller;
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

    private void setCurrentTestCaseId(Method method) {
        if (method.isAnnotationPresent(TestCaseId.class)) {
            currentTestCaseId.set(method.getAnnotation(TestCaseId.class).value());
        } else {
            // Optional: If no annotation found
            currentTestCaseId.set(method.getName());
        }
    }
    @AfterMethod
    public void teardown() {
        if (controller != null) controller.teardown();
        softAssert.assertAll();
    }

    @AfterSuite(alwaysRun = true)
    public void tearDownSuite() {
        // Use the global props initialized during the init phase
        ReportUtility.generateAllureReport(envProps);
    }
}
