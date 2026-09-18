# Codinglair TAF 1.2.0 consumer dependencies

Use capability starters for ordinary projects. All coordinates use `com.codinglair.taf`. The BOM
aligns compatible versions but adds no capability. Use only organization-approved repositories.

## Maven

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.codinglair.taf</groupId>
      <artifactId>codinglair-taf-bom</artifactId>
      <version>1.2.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
<dependencies>
  <dependency>
    <groupId>com.codinglair.taf</groupId>
    <artifactId>codinglair-taf-starter-web</artifactId>
    <version>1.2.0</version>
    <type>pom</type>
  </dependency>
</dependencies>
```

Replace the final artifact with any starter below or declare several starters together. Starter
POMs are dependency aggregators, so Maven requires `type` `pom`.

## Gradle Groovy

```groovy
dependencies {
    implementation platform("com.codinglair.taf:codinglair-taf-bom:1.2.0")
    implementation "com.codinglair.taf:codinglair-taf-starter-web:1.2.0@pom"
}
```

## Gradle Kotlin

```kotlin
dependencies {
    implementation(platform("com.codinglair.taf:codinglair-taf-bom:1.2.0"))
    implementation("com.codinglair.taf:codinglair-taf-starter-web:1.2.0@pom")
}
```

These Gradle declarations cover dependency syntax. Release 1.2.0 does not support Gradle
blueprint/project generation or promise Maven-plugin equivalence.

## Selection and composition

| Capability | Recommended starter | Consumer choice |
| --- | --- | --- |
| Web | `codinglair-taf-starter-web` | named Playwright controller |
| API | `codinglair-taf-starter-api` | named REST controller |
| Database | `codinglair-taf-starter-database` | JDBC driver and named connection |
| Mobile | `codinglair-taf-starter-mobile` | Appium server, Android target, named controller |
| Kafka | `codinglair-taf-starter-messaging-kafka` | bootstrap servers and named controller |
| RabbitMQ | `codinglair-taf-starter-messaging-rabbitmq` | addresses and named controller |
| JMS | `codinglair-taf-starter-messaging-jms` | JMS client, `ConnectionFactory`, named controller |
| EventBridge/SQS | `codinglair-taf-starter-messaging-aws` | named AWS profile and service instance |

Provider starters transitively include `codinglair-taf-starter-messaging`. That generic starter is
only for a custom `MessagingProvider`; it contains no provider. Classpath presence never activates
a capability. Top-level starters share the Runtime/TestSession, environment, secrets,
test-definition, reporting, and TestNG foundation. Normal mediation and the BOM select one version.
Cucumber is an independent opt-in runner (`codinglair-taf-runner-cucumber`).

## Exclusions, direct modules, and internal artifacts

For externally managed infrastructure, Kafka, RabbitMQ, and AWS starters support excluding their
optional Testcontainers integration. Do not exclude the common foundation or provider
implementation. Database consumers select a JDBC driver; JMS consumers select a JMS client.

Advanced consumers may select supported direct capability modules such as `taf-web-playwright`,
`taf-api-rest`, `taf-database`, `taf-mobile-appium`, or a `taf-messaging-*` provider. They then own
the full lifecycle, runner, reporting, secrets, environment, and test-definition graph.

Do not select `codinglair-taf-common`, `taf-mobile-core`, or `taf-messaging-core` as standalone
capabilities. Do not add `codinglair-taf-mcp`, `taf-mcp-*`, `taf-execution-worker`, demo/example,
fixture, build-support, or proprietary/internal artifacts to a Runtime project. Operate MCP through
the official image.

The authoritative mapping, activation keys, optional integrations, and exclusions are in
[`starter-capability-manifest-v1.json`](starter-capability-manifest-v1.json).
