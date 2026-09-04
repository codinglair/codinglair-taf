package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsumerProjectValidatorTest {
  @TempDir Path temp;
  private final ConsumerProjectValidator validator = new ConsumerProjectValidator();

  @Test
  void deliberatelyConformingGoldenFixturePassesAndIsConcurrent() throws Exception {
    Path project = fixture("golden");
    ConformanceCli.main(new String[] {project.toString()});
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<ConformanceReport> reports =
          executor
              .invokeAll(
                  java.util.Collections.nCopies(
                      50,
                      (java.util.concurrent.Callable<ConformanceReport>)
                          () -> validator.validate(project)))
              .stream()
              .map(
                  future -> {
                    try {
                      return future.get();
                    } catch (Exception e) {
                      throw new AssertionError(e);
                    }
                  })
              .toList();
      assertThat(reports).allMatch(ConformanceReport::conforms);
    }
  }

  @Test
  void invalidFixturesDetectEveryAssignmentCategory() throws Exception {
    Path project = fixture("invalid");
    write(
        project,
        "src/main/java/example/TestSessionLifecycle.java",
        "package example; class TestSessionLifecycle { void x(){ new TestSession(); } }");
    write(
        project,
        "src/main/java/example/OwnedController.java",
        "package example; class OwnedController implements TestController {}");
    write(
        project,
        "src/test/java/example/BadTest.java",
        "package example; import io.qameta.allure.Step; class BadTest implements ITestListener { TestSession s; @Test void x(){ PlaywrightController p=null; } }");
    write(
        project,
        "src/test/resources/application-ci.yaml",
        "base-url: https://internal.example\npassword: literal-secret\n");
    Files.delete(project.resolve("src/main/java/example/page/Page.java"));
    Files.delete(project.resolve("src/main/java/example/page/LoginPage.java"));
    Files.delete(project.resolve("src/main/java/example/page"));
    Files.delete(project.resolve("src/main/java/example/workflow/Workflow.java"));
    Files.delete(project.resolve("src/main/java/example/workflow"));
    String pom =
        Files.readString(project.resolve("pom.xml"))
            .replace(
                "</dependencies>",
                "<dependency><groupId>com.codinglair.taf</groupId><artifactId>codinglair-taf-mcp</artifactId></dependency></dependencies>");
    Files.writeString(project.resolve("pom.xml"), pom);

    assertThat(validator.validate(project).violations())
        .extracting(ConformanceViolation::rule)
        .contains(
            "lifecycle.framework-owned",
            "lifecycle.consumer-owned",
            "lifecycle.listener-owned",
            "reporting.vendor-boundary",
            "configuration.externalized",
            "traceability.testng",
            "architecture.page",
            "dependency.prohibited");
  }

  @Test
  void selectedAndUnselectedDependenciesAndStrictDescriptorAreChecked() throws Exception {
    Path project = fixture("selection");
    String pom =
        Files.readString(project.resolve("pom.xml"))
            .replace(
                "<artifactId>taf-web-playwright</artifactId>",
                "<artifactId>cucumber-java</artifactId>");
    Files.writeString(project.resolve("pom.xml"), pom);
    assertThat(validator.validate(project).violations())
        .extracting(ConformanceViolation::rule)
        .contains("dependency.selected", "dependency.unselected");
    Files.writeString(
        project.resolve("src/test/resources/taf-project.json"),
        descriptor().replace("\"reporting\"", "\"unexpected\":true,\"reporting\""));
    assertThat(validator.validate(project).violations())
        .extracting(ConformanceViolation::rule)
        .contains("descriptor.schema");
  }

  @Test
  void missingProfilesAndCucumberTraceabilityAreActionable() throws Exception {
    Path project = fixture("profiles");
    Files.delete(project.resolve("src/test/resources/application-local.yaml"));
    write(
        project,
        "src/test/resources/features/example.feature",
        "Feature: x\nScenario: untraced\n Given x\n");
    assertThat(validator.validate(project).violations())
        .extracting(ConformanceViolation::rule)
        .contains("spring.profile", "traceability.cucumber");
  }

  @Test
  void duplicateTraceabilityAndHardCodedJavaEndpointsAreRejected() throws Exception {
    Path project = fixture("duplicates");
    write(
        project,
        "src/test/java/example/DuplicateTest.java",
        "package example; class DuplicateTest extends TafBaseTest { @TestCaseId(\"TC1\") @Test void duplicate(){} }");
    write(
        project,
        "src/main/java/example/Endpoint.java",
        "package example; class Endpoint { String url = \"https://internal.example\"; }");
    assertThat(validator.validate(project).violations())
        .extracting(ConformanceViolation::rule)
        .contains("traceability.duplicate", "configuration.externalized");
  }

  @Test
  void reportInspectionRejectsDuplicatesAndMissingHierarchy() throws Exception {
    Path report = temp.resolve("report.json");
    Files.writeString(
        report,
        "{\"events\":[{\"id\":\"one\",\"level\":\"TEST\"},{\"id\":\"one\",\"level\":\"ACTION\"}]}");
    assertThat(validator.validateReportOutput(report).violations())
        .extracting(ConformanceViolation::rule)
        .contains("report.duplicate", "report.hierarchy");
    Files.writeString(
        report,
        "{\"events\":[{\"id\":\"one\",\"level\":\"TEST\"},{\"id\":\"two\",\"level\":\"VALIDATION\"}]}");
    assertThat(validator.validateReportOutput(report).conforms()).isTrue();
  }

  @Test
  void sad18StructuralRulesRejectDeterministicViolations() throws Exception {
    Path project = fixture("sad18-invalid");
    write(
        project,
        "src/main/java/example/page/BadPage.java",
        """
        package example.page;
        import com.example.workflow.LoginWorkflow;
        class BadPage {
          static Locator locator;
          void open(Page page) { page.locator("#login"); }
        }
        """);
    write(
        project,
        "src/main/java/example/Selectors.java",
        "package example; class Selectors { static final LocatorSpec LOGIN = null; }");
    write(
        project,
        "src/test/java/example/BadLifecycleTest.java",
        """
        package example;
        class BadLifecycleTest extends TafBaseTest {
          @TestCaseId("TC2") @Test void bad() {
            service(LoginWorkflow.class); beginTest(); new PlaywrightController();
          }
          void executeReported() {}
        }
        """);

    assertThat(validator.validate(project).violations())
        .extracting(ConformanceViolation::rule)
        .contains(
            "locator.inline-construction",
            "state.static-session-bound",
            "locator.owner",
            "lifecycle.wrapper",
            "lifecycle.manual",
            "dependency.service-lookup",
            "dependency.direct-construction",
            "dependency.package-direction");
  }

  @Test
  void explicitSubjectActionsAndPrerequisiteWorkflowsBothPass() throws Exception {
    Path project = fixture("abstraction-styles");
    write(
        project,
        "src/test/java/example/GoodTest.java",
        """
        package example;
        class GoodTest extends TafBaseTest {
          @TestCaseId("TC1") @Test void loginIsTheSubject() { loginPage.submit(); }
          @TestCaseId("TC2") @Test void checkoutUsesPrerequisite() { loginWorkflow.login(); checkoutPage.submit(); }
        }
        """);
    assertThat(validator.validate(project).conforms()).isTrue();
  }

  @Test
  void requiredAssetsPropertyAuthorityAndTraceabilityResourcesAreChecked() throws Exception {
    Path project = fixture("resource-invalid");
    Files.delete(project.resolve("src/main/java/example/ConsumerProperties.java"));
    Files.delete(project.resolve("src/test/java/example/ContextTest.java"));
    Files.delete(project.resolve("src/test/resources/testng.xml"));
    Files.delete(project.resolve("src/test/resources/test-data/TC1.json"));
    Files.delete(project.resolve("src/test/resources/test-data/TC2.json"));
    Files.delete(project.resolve("src/test/resources/test-data"));
    write(project, "src/test/resources/application-local.yaml", "local-only: value\n");
    assertThat(validator.validate(project).violations())
        .extracting(ConformanceViolation::rule)
        .contains(
            "spring.typed-configuration",
            "spring.context-test",
            "spring.suite",
            "test-data.required",
            "configuration.property-authority");
  }

  @Test
  void duplicateProfilesPropertiesAndUnresolvedCucumberIdsAreRejected() throws Exception {
    Path project = fixture("duplicate-resources");
    write(
        project,
        "src/test/resources/application.yaml",
        "base-url: ${SAUCE_URL:REPLACE_ME}\nbase-url: ${OTHER_URL:REPLACE_ME}\n");
    String duplicateProfile = "base-url: ${PROFILE_URL:REPLACE_ME}\n";
    write(project, "src/test/resources/application-local.yaml", duplicateProfile);
    write(project, "src/test/resources/application-ci.yaml", duplicateProfile);
    write(
        project,
        "src/test/resources/features/duplicate.feature",
        "@test-case-MISSING\nFeature: duplicate\n@test-case-MISSING\nScenario: first\n Given x\n");

    assertThat(validator.validate(project).violations())
        .extracting(ConformanceViolation::rule)
        .contains(
            "configuration.property-duplicate",
            "configuration.profile-duplicate",
            "traceability.duplicate",
            "traceability.unresolved");
  }

  private Path fixture(String name) throws IOException {
    Path root = temp.resolve(name);
    write(
        root,
        "pom.xml",
        "<project><dependencies>"
            + "<dependency><artifactId>taf-web-playwright</artifactId></dependency>"
            + "<dependency><artifactId>codinglair-taf-runner-testng</artifactId></dependency>"
            + "<dependency><artifactId>taf-test-definitions</artifactId></dependency>"
            + "</dependencies></project>");
    write(root, "src/test/resources/taf-project.json", descriptor());
    write(
        root,
        "src/test/resources/application.yaml",
        "base-url: ${SAUCE_URL:REPLACE_ME}\nsecret-ref: ${SAUCE_SECRET_REF:REPLACE_ME}\n");
    write(
        root,
        "src/test/resources/application-local.yaml",
        "base-url: ${LOCAL_SAUCE_URL:REPLACE_ME}\n");
    write(
        root,
        "src/test/resources/application-ci.yaml",
        "secret-ref: ${CI_SAUCE_SECRET_REF:REPLACE_ME}\n");
    write(
        root,
        "src/main/java/example/App.java",
        "package example; @SpringBootApplication class App {}");
    write(root, "src/main/java/example/page/Page.java", "package example.page; class Page {}");
    write(
        root,
        "src/main/java/example/page/LoginPage.java",
        "package example.page; class LoginPage { private static final LocatorSpec LOGIN = null; void submit(){} }");
    write(
        root,
        "src/main/java/example/ConsumerProperties.java",
        "package example; @ConfigurationProperties(\"consumer\") record ConsumerProperties(String baseUrl) {}");
    write(
        root,
        "src/main/java/example/workflow/Workflow.java",
        "package example.workflow; class Workflow {}");
    write(
        root,
        "src/test/java/example/GoodTest.java",
        "package example; class GoodTest extends TafBaseTest { @TestCaseId(\"TC1\") @Test void works(){} }");
    write(
        root,
        "src/test/java/example/ContextTest.java",
        "package example; @SpringBootTest class ContextTest {}");
    write(
        root,
        "src/test/java/example/CucumberLifecycle.java",
        "package example; class CucumberLifecycle extends TafCucumberHooks {}");
    write(
        root,
        "src/test/resources/testng.xml",
        "<suite name=\"consumer\"><test name=\"tests\"/></suite>");
    write(root, "src/test/resources/test-data/TC1.json", "{}");
    write(root, "src/test/resources/test-data/TC2.json", "{}");
    return root;
  }

  private static String descriptor() {
    return "{\"schemaVersion\":\"1.0\",\"project\":{\"name\":\"golden\",\"type\":\"focused-golden\",\"basePackage\":\"com.example\"},"
        + "\"runtime\":{\"version\":\"${taf.version}\"},\"capabilities\":[\"web-playwright\"],\"runners\":[\"testng\"],"
        + "\"testDefinitions\":{\"provider\":\"file-csv\",\"authoritative\":true},\"reporting\":{\"adapter\":\"allure\"}}";
  }

  private static void write(Path root, String relative, String content) throws IOException {
    Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
