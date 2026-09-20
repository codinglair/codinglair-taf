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

/** Approved version 1.0 physical blueprint contributions for release 1.2.0. */
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
            asset(".gitignore", "target/\n.idea/\n*.iml\n", false),
            asset("README.md", readme(), false),
            asset("src/test/resources/application.yml", commonConfiguration(), false),
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
        List.of(),
        runner == Runner.CUCUMBER_TESTNG
            ? List.of(
                asset(
                    "src/test/resources/features/generated-project.feature",
                    "Feature: Generated project preflight\n  Scenario: Project is composed\n    Then the generated project is ready\n",
                    false))
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
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
              </plugin>
            </plugins>
          </build>
        </project>
        """;
  }

  private static String preflightTest() {
    return """
        package __BASE_PACKAGE__;

        import com.codinglair.taf.runtime.core.TestSession;
        import org.testng.annotations.Test;

        public final class BlueprintPreflightTest {
          @Test
          public void createsAndClosesAnIsolatedTestSession() {
            try (TestSession session = TestSession.create()) {
              if (session == null) {
                throw new AssertionError("TestSession was not created");
              }
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
