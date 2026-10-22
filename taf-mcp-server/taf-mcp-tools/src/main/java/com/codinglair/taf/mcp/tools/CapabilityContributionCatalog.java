package com.codinglair.taf.mcp.tools;

import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Asset;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Capability;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.ConfigurationClaim;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Contribution;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Kind;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.MergeRule;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.MessagingProvider;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Reporting;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Runner;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Selector;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.StarterManifest;
import java.util.List;
import java.util.Map;

/** Approved version 1.0 physical blueprint contributions, including additive Apple selections. */
public final class CapabilityContributionCatalog {
  public static final String BLUEPRINT_VERSION = "1.0";

  private CapabilityContributionCatalog() {}

  /** Returns the immutable authoritative starter coordinates understood by this blueprint. */
  public static StarterManifest starterManifest() {
    return new StarterManifest(
        "com.codinglair.taf",
        Map.ofEntries(
            Map.entry("starter-web", "codinglair-taf-starter-web"),
            Map.entry("starter-api", "codinglair-taf-starter-api"),
            Map.entry("starter-database", "codinglair-taf-starter-database"),
            Map.entry("starter-mobile", "codinglair-taf-starter-mobile"),
            Map.entry("runner-testng", "codinglair-taf-runner-testng"),
            Map.entry("runner-cucumber", "codinglair-taf-runner-cucumber"),
            Map.entry("messaging-kafka", "codinglair-taf-starter-messaging-kafka"),
            Map.entry("messaging-rabbitmq", "codinglair-taf-starter-messaging-rabbitmq"),
            Map.entry("messaging-jms", "codinglair-taf-starter-messaging-jms"),
            Map.entry("messaging-aws", "codinglair-taf-starter-messaging-aws")));
  }

  /** Returns contributions in canonical order. Callers may safely retain or shuffle the result. */
  public static List<Contribution> contributions() {
    return List.of(
        common(),
        capability(Capability.API, 200, "starter-api", apiAssets(), "/taf/api"),
        capability(Capability.DATABASE, 201, "starter-database", databaseAssets(), "/taf/database"),
        capability(Capability.MESSAGING, 202, null, messagingAssets(), "/taf/messaging/selection"),
        capability(Capability.MOBILE, 203, "starter-mobile", mobileAssets(), "/taf/mobile"),
        capability(Capability.WEB, 204, "starter-web", webAssets(), "/taf/web"),
        provider(MessagingProvider.AWS, 300, "messaging-aws", "aws"),
        provider(MessagingProvider.JMS, 301, "messaging-jms", "jms"),
        provider(MessagingProvider.KAFKA, 302, "messaging-kafka", "kafka"),
        provider(MessagingProvider.RABBITMQ, 303, "messaging-rabbitmq", "rabbitmq"),
        runner(Runner.TESTNG, 400),
        runner(Runner.CUCUMBER_TESTNG, 401),
        reporting(Reporting.ALLURE, 500),
        reporting(Reporting.NONE, 501));
  }

  private static Contribution common() {
    return new Contribution(
        "common",
        BLUEPRINT_VERSION,
        Kind.COMMON,
        new Selector(Kind.COMMON, null, null, null, null),
        100,
        List.of(),
        List.of(
            asset("pom.xml", pom(), true),
            asset("mvnw", unixWrapper(), false),
            asset("mvnw.cmd", windowsWrapper(), false),
            asset(".mvn/maven.config", "-ntp\n", false),
            asset(".mvn/wrapper/maven-wrapper.properties", wrapperProperties(), false),
            asset(".gitignore", "target/\n.idea/\n*.iml\n", false),
            asset("README.md", readme(), false),
            asset("src/test/resources/application.yml", commonConfiguration(), false),
            asset(
                "src/test/resources/application-local.yml",
                "spring:\n  config:\n    activate:\n      on-profile: local\n",
                false),
            asset(
                "src/test/resources/application-ci.yml",
                "spring:\n  config:\n    activate:\n      on-profile: ci\n",
                false),
            asset("src/test/resources/testng.xml", testNgSuite(), false),
            asset("src/test/resources/taf-project.json", descriptor(), false),
            asset(
                "src/test/java/__PACKAGE_PATH__/BlueprintPreflightTest.java",
                preflightTest(),
                false)),
        List.of(
            claim("/taf/secrets", "secret references only"), claim("/taf/environment", "local")));
  }

  private static Contribution capability(
      Capability capability,
      int order,
      String manifestId,
      List<Asset> assets,
      String configurationPointer) {
    return new Contribution(
        "capability-" + capability.name().toLowerCase(),
        BLUEPRINT_VERSION,
        Kind.CAPABILITY,
        new Selector(Kind.CAPABILITY, capability, null, null, null),
        order,
        manifestId == null ? List.of() : List.of(manifestId),
        assets,
        List.of(claim(configurationPointer, capability.name().toLowerCase())));
  }

  private static Contribution provider(
      MessagingProvider provider, int order, String manifestId, String name) {
    return new Contribution(
        "provider-" + name,
        BLUEPRINT_VERSION,
        Kind.PROVIDER,
        new Selector(Kind.PROVIDER, null, provider, null, null),
        order,
        List.of(manifestId),
        List.of(
            asset(
                "src/test/resources/capabilities/messaging-" + name + ".yml",
                messagingProviderConfiguration(name),
                false)),
        List.of(claim("/taf/messaging/providers/" + name, name)));
  }

  private static Contribution runner(Runner runner, int order) {
    String name = runner.name().toLowerCase().replace('_', '-');
    return new Contribution(
        "runner-" + name,
        BLUEPRINT_VERSION,
        Kind.RUNNER,
        new Selector(Kind.RUNNER, null, null, runner, null),
        order,
        runner == Runner.CUCUMBER_TESTNG
            ? List.of("runner-testng", "runner-cucumber")
            : List.of("runner-testng"),
        runner == Runner.CUCUMBER_TESTNG
            ? List.of(
                asset(
                    "src/test/resources/features/generated-project.feature",
                    "Feature: Generated project preflight\n  Scenario: Project is composed\n    Then the generated project is ready\n",
                    false),
                asset(
                    "src/test/java/__PACKAGE_PATH__/bdd/GeneratedCucumberRunner.java",
                    cucumberRunner(),
                    false),
                asset(
                    "src/test/java/__PACKAGE_PATH__/bdd/GeneratedSteps.java",
                    cucumberSteps(),
                    false),
                asset("src/test/resources/testng-bdd.xml", cucumberSuite(), false))
            : List.of(),
        List.of(claim("/taf/runner", name)));
  }

  private static Contribution reporting(Reporting reporting, int order) {
    String name = reporting.name().toLowerCase();
    return new Contribution(
        "reporting-" + name,
        BLUEPRINT_VERSION,
        Kind.REPORTING,
        new Selector(Kind.REPORTING, null, null, null, reporting),
        order,
        List.of(),
        reporting == Reporting.ALLURE
            ? List.of(
                asset(
                    "src/test/resources/allure.properties",
                    "allure.results.directory=target/allure-results\n",
                    false))
            : List.of(),
        List.of(claim("/taf/reporting", name)));
  }

  private static List<Asset> webAssets() {
    return List.of(
        asset(
            "src/test/resources/capabilities/web.yml",
            "taf:\n  web:\n    playwright:\n      enabled: false\n      instances:\n        primary:\n          browser: chromium\n",
            false),
        example(
            "WebPageObjectExample",
            "Web uses Page Object and Page Component Object conventions; enable Playwright only in an equipped environment."));
  }

  private static List<Asset> apiAssets() {
    return List.of(
        asset(
            "src/test/resources/capabilities/api.yml",
            "taf:\n  api:\n    rest:\n      enabled: false\n      instances:\n        primary:\n          base-url: https://example.invalid\n    soap:\n      enabled: false\n",
            false),
        example(
            "ApiObjectModelExample",
            "API Object Model example for REST; SOAP is an explicit optional integration."));
  }

  private static List<Asset> databaseAssets() {
    return List.of(
        asset(
            "src/test/resources/capabilities/database.yml",
            "taf:\n  database:\n    enabled: false\n    connections:\n      primary:\n        jdbc-url: jdbc:postgresql://localhost:5432/example\n        username-secret: secret://local/database/username\n        password-secret: secret://local/database/password\n",
            false),
        example(
            "NamedDatabaseConnectionExample",
            "Database examples resolve the named connection 'primary'; infrastructure remains provider-owned."));
  }

  private static List<Asset> messagingAssets() {
    return List.of(
        example(
            "NamedMessagingProviderExample",
            "Messaging examples use the explicitly selected named provider; generation rejects an absent provider."));
  }

  private static List<Asset> mobileAssets() {
    return List.of(
        asset(
            "src/test/resources/capabilities/mobile.yml",
            "taf:\n  mobile:\n    android:\n      enabled: false\n      instances:\n        primary:\n          automation-name: UiAutomator2\n          server-url: http://127.0.0.1:4723\n",
            false),
        example(
            "AndroidObjectModelExample",
            "Android/UiAutomator2 is the default; devices, applications, and Appium servers are provider-owned."));
  }

  private static Asset example(String name, String description) {
    return asset(
        "src/test/java/__PACKAGE_PATH__/examples/" + name + ".java",
        """
        package __BASE_PACKAGE__.examples;

        /** %s */
        public final class %s {
          public static final String NAMED_INSTANCE = "primary";

          private %s() {}
        }
        """
            .formatted(description, name, name),
        false);
  }

  private static String pom() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>__GROUP_ID__</groupId>
          <artifactId>__ARTIFACT_ID__</artifactId>
          <version>1.0-SNAPSHOT</version>
          <properties>
            <maven.compiler.release>25</maven.compiler.release>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            <taf.version>__TAF_VERSION__</taf.version>
          </properties>
          <dependencyManagement>
            <dependencies>
              <dependency>
                <groupId>com.codinglair.taf</groupId>
                <artifactId>codinglair-taf-bom</artifactId>
                <version>${taf.version}</version>
                <type>pom</type>
                <scope>import</scope>
              </dependency>
            </dependencies>
          </dependencyManagement>
          <dependencies>
        __TAF_DEPENDENCIES__
        __TAF_REPORTING_DEPENDENCIES__
          </dependencies>
          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
              </plugin>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.4.2</version>
              </plugin>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
                <dependencies>
                  <dependency>
                    <groupId>org.apache.maven.surefire</groupId>
                    <artifactId>surefire-testng</artifactId>
                    <version>3.5.2</version>
                  </dependency>
                </dependencies>
                <configuration>
                  <suiteXmlFiles>
                    <suiteXmlFile>src/test/resources/testng.xml</suiteXmlFile>
                  </suiteXmlFiles>
                </configuration>
              </plugin>
              <plugin>
                <groupId>com.codinglair.taf</groupId>
                <artifactId>taf-consumer-conformance-maven-plugin</artifactId>
                <version>${taf.version}</version>
                <executions>
                  <execution>
                    <id>validate-taf-consumer</id>
                    <goals>
                      <goal>validate</goal>
                      <goal>validate-compiled-tests</goal>
                    </goals>
                  </execution>
                </executions>
              </plugin>
            </plugins>
          </build>
        </project>
        """;
  }

  private static String preflightTest() {
    return """
        package __BASE_PACKAGE__;

        import com.codinglair.taf.core.annotation.reporting.TestCaseId;
        import org.testng.annotations.Test;

        public final class BlueprintPreflightTest extends com.codinglair.taf.runtime.testng.TafBaseTest {
          @Test
          @TestCaseId("GENERATED-PREFLIGHT-001")
          public void createsAndClosesAnIsolatedTestSession() {
            if (testSession() == null) {
              throw new AssertionError("TafBaseTest did not create the invocation session");
            }
          }
        }
        """;
  }

  private static String commonConfiguration() {
    return """
        taf:
          environment: local
          secrets:
            provider: env
            references-only: true
        """;
  }

  private static String testNgSuite() {
    return """
        <!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
        <suite name="taf-consumer">
          <test name="ordinary">
            <classes><class name="__BASE_PACKAGE__.BlueprintPreflightTest"/></classes>
          </test>
        </suite>
        """;
  }

  private static String cucumberRunner() {
    return """
        package __BASE_PACKAGE__.bdd;

        import com.codinglair.taf.runtime.cucumber.AbstractCucumberRunner;
        import io.cucumber.testng.CucumberOptions;

        @CucumberOptions(
            features = "classpath:features/generated-project.feature",
            glue = {"__BASE_PACKAGE__.bdd", "com.codinglair.taf.runtime.cucumber"})
        public final class GeneratedCucumberRunner extends AbstractCucumberRunner {}
        """;
  }

  private static String cucumberSuite() {
    return """
        <!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
        <suite name="taf-consumer-bdd">
          <test name="bdd"><classes>
            <class name="__BASE_PACKAGE__.bdd.GeneratedCucumberRunner"/>
          </classes></test>
        </suite>
        """;
  }

  private static String cucumberSteps() {
    return """
        package __BASE_PACKAGE__.bdd;

        import com.codinglair.taf.runtime.cucumber.CucumberScenarioSession;
        import io.cucumber.java.en.Then;

        public final class GeneratedSteps {
          private final CucumberScenarioSession scenarioSession;

          public GeneratedSteps(CucumberScenarioSession scenarioSession) {
            this.scenarioSession = scenarioSession;
          }

          @Then("the generated project is ready")
          public void generatedProjectIsReady() {
            if (scenarioSession.session() == null) {
              throw new AssertionError("TafCucumberHooks did not open the scenario TestSession");
            }
          }
        }
        """;
  }

  private static String descriptor() {
    return """
        {
          "schemaVersion": "1.2",
          "project": {
            "name": "__ARTIFACT_ID__",
            "type": "standalone-test-automation",
            "basePackage": "__BASE_PACKAGE__",
            "expectedBuildRoot": ".",
            "sutInteraction": "external"
          },
          "runtime": { "version": "__TAF_VERSION__" },
          "capabilities": [__CAPABILITIES__],
          "runners": [__RUNNERS__],
          "testDefinitions": { "provider": "file-csv", "authoritative": true },
          "reporting": { "adapter": "allure" }
        }
        """;
  }

  private static String unixWrapper() {
    return """
        #!/bin/sh
        set -eu
        BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
        URL=$(sed -n 's/^distributionUrl=//p' "$BASE_DIR/.mvn/wrapper/maven-wrapper.properties")
        ARCHIVE=${URL##*/}
        MAVEN_HOME="$BASE_DIR/.mvn/wrapper/dists/${ARCHIVE%.zip}"
        if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
          mkdir -p "$MAVEN_HOME.tmp"
          if command -v curl >/dev/null 2>&1; then curl -fsSL "$URL" -o "$MAVEN_HOME.tmp/$ARCHIVE";
          elif command -v wget >/dev/null 2>&1; then wget -q "$URL" -O "$MAVEN_HOME.tmp/$ARCHIVE";
          else echo "Maven Wrapper requires curl or wget" >&2; exit 1; fi
          unzip -q "$MAVEN_HOME.tmp/$ARCHIVE" -d "$MAVEN_HOME.tmp"
          mv "$MAVEN_HOME.tmp"/apache-maven-* "$MAVEN_HOME"
          rm -rf "$MAVEN_HOME.tmp"
        fi
        exec "$MAVEN_HOME/bin/mvn" "$@"
        """;
  }

  private static String windowsWrapper() {
    return """
        @echo off
        setlocal
        set "BASE_DIR=%~dp0"
        for /f "tokens=1,* delims==" %%A in ('findstr /b distributionUrl "%BASE_DIR%.mvn\\wrapper\\maven-wrapper.properties"') do set "URL=%%B"
        for %%A in ("%URL%") do set "ARCHIVE=%%~nxA"
        set "MAVEN_HOME=%BASE_DIR%.mvn\\wrapper\\dists\\%ARCHIVE:.zip=%"
        if not exist "%MAVEN_HOME%\\bin\\mvn.cmd" powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $u=$env:URL; $a=Join-Path $env:TEMP $env:ARCHIVE; Invoke-WebRequest $u -OutFile $a; $t=$env:MAVEN_HOME+'.tmp'; New-Item -ItemType Directory -Force $t|Out-Null; Expand-Archive $a $t -Force; Move-Item (Get-ChildItem $t -Directory | Select-Object -First 1).FullName $env:MAVEN_HOME; Remove-Item $t -Recurse -Force"
        call "%MAVEN_HOME%\\bin\\mvn.cmd" %*
        exit /b %ERRORLEVEL%
        """;
  }

  private static String wrapperProperties() {
    return """
        wrapperVersion=3.3.4
        distributionType=only-script
        distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip
        """;
  }

  private static String messagingProviderConfiguration(String provider) {
    return "taf:\n  messaging:\n    "
        + provider
        + ":\n      enabled: false\n      controllers:\n        primary: {}\n";
  }

  private static String readme() {
    return """
        # __ARTIFACT_ID__

        Generated from Codinglair TAF blueprint 1.0 for Java 25. Dependencies are public
        capability/provider starters. Configuration contains secret references, never values.

        Run the generated preflight with `mvn test` after the selected TAF version is available.
        """;
  }

  private static Asset asset(String path, String content, boolean dependencyMetadata) {
    return new Asset(path, content, dependencyMetadata);
  }

  private static ConfigurationClaim claim(String pointer, String value) {
    return new ConfigurationClaim(
        "src/test/resources/application.yml", pointer, MergeRule.DEEP_MERGE_NO_OVERWRITE, value);
  }
}
