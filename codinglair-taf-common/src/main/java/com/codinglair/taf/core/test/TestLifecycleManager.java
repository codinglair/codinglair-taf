package com.codinglair.taf.core.test;

import com.codinglair.taf.core.AppConfigLoader;
import com.codinglair.taf.core.controller.abstraction.TestController;
import com.codinglair.taf.core.controller.factory.TestControllerFactory;
import com.codinglair.taf.core.data.abstraction.TestContext;
import com.codinglair.taf.core.data.factory.TestContextFactory;
import com.codinglair.taf.core.environment.EnvironmentProperties;
import com.codinglair.taf.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.core.reporting.factory.ReporterFactory;
import com.codinglair.taf.core.utils.secret.abstraction.SecretManager;
import com.codinglair.taf.core.utils.secret.factory.SecretManagerFactory;
import org.assertj.core.api.SoftAssertions;
import org.testng.ITestContext;

import java.util.Map;

public class TestLifecycleManager {
    private TestController controller;
    private TestReporter reporter;
    private TestContext<?,?> context;
    private EnvironmentProperties envProps;
    private static SecretManager secretManager;
    private SoftAssertions softAssert;
    private String testCaseId;

    /**
     * Executed in TestNG @BeforeSuite method
     */
    public void configSuite() {
        //Setting up Environment
        envProps = new EnvironmentProperties(
                AppConfigLoader.loadSubmoduleConfig("config/app.config")
                        .getProperty("ENV_PROPERTIES_PATH"));

        //Initializing text context
        String contextClassName = AppConfigLoader.loadSubmoduleConfig("config/app.config")
                .getProperty("TEST_CONTEXT_CLASS");
        context = TestContextFactory.createContext(contextClassName, envProps);

        //Setting up for multi-assertion validations, to prevent a failure during any first assert
        softAssert = new SoftAssertions();
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

        //Injecting additional environment properties from TestNG context.
        envProps.putEnvProperty("report_name", testNgContext.getName());
        //Injecting potential additional env parameters from the test suite.
        Map<String, String> allParams = testNgContext.getCurrentXmlTest().getAllParameters();
        enhanceEnvironment(allParams);

        // Initialize Controller. Parameter name "controllerClass" must be present either
        // in test suite or in app.config, and it must specify the controller implementation.
        String controllerClass = getControllerClassName(allParams);
        this.controller = TestControllerFactory.create(controllerClass);

        // Pass the fully merged properties to the controller
        controller.setup(envProps);

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

    private void setCurrentTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }

    public String getActiveTestCaseId() {
        return testCaseId;
    }

    @SuppressWarnings("unchecked")
    public <T extends TestController> T getController() {
        return (T) this.controller;
    }

    public TestReporter getReporter() {
        return reporter;
    }

    @SuppressWarnings("unchecked")
    public TestContext<?, ?> getContext() {
        return context;
    }

    public EnvironmentProperties getEnvProps() {
        return envProps;
    }

    public static SecretManager getSecretManager() {
        return secretManager;
    }

    public SoftAssertions getSoftAssert() {
        return softAssert;
    }
}
