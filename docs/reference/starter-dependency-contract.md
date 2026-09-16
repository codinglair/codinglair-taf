# Starter dependency contract

The versioned [capability manifest](starter-capability-manifest-v1.json) is the authoritative
machine-readable mapping from capability or messaging provider to its public starter coordinate,
baseline implementation modules, required configuration, optional integrations, and supported
exclusions. Starter POMs must reproduce that mapping; documentation, scaffolding, MCP discovery,
and consumer conformance must consume or validate it rather than maintain a second graph.

Importing `com.codinglair.taf:codinglair-taf-bom` only supplies compatible versions. It does not
install or activate a capability. Advanced consumers may continue to declare any supported direct
module listed in the BOM.

## Dependency graphs

Each top-level starter has this graph, where the capability leaf is defined by the manifest:

```text
codinglair-taf-starter-<capability> (pom)
+-- capability implementation module(s)
+-- codinglair-taf-runtime-core
+-- taf-environments
+-- taf-secrets-api
+-- taf-secrets-local (available, never implicitly active)
+-- taf-test-definitions
+-- codinglair-taf-reporting-allure
`-- codinglair-taf-runner-testng
```

Messaging uses a two-level graph:

```text
codinglair-taf-starter-messaging-<provider> (pom)
+-- codinglair-taf-starter-messaging (pom)
|   +-- shared foundation shown above
|   `-- taf-messaging-core
`-- taf-messaging-<provider>
```

The generic messaging graph contains no Kafka, RabbitMQ, JMS-provider, AWS SDK, or concrete
provider implementation. A provider starter contributes exactly its provider implementation.
Multiple provider starters therefore share one generic foundation through Maven mediation.

## Configuration and exclusions

Presence is not activation. Every operational capability requires the explicit enabled and named
instance/connection configuration recorded in the manifest. The local secrets provider likewise
requires explicit configuration or the documented local profile and is never a production
fallback. Select one provider with `taf.secrets.provider=<provider-id-or-bean-name>`. For multiple
providers, map each reference provider ID to exactly one bean name with
`taf.secrets.routing.<provider-id>=<bean-name>`. The `taf-local` Spring profile is the documented
explicit local shortcut and activates only `env`; Jasypt remains explicitly selected. Missing,
unavailable, duplicate, or mixed single-provider/routing selection fails startup with sanitized
capability, field/profile, and corrective-action details.

Supported exclusions remove only integrations identified in the manifest, such as a managed
Testcontainers provider when infrastructure is external. Excluding a baseline implementation or
shared-foundation module is unsupported because it creates a misleading partial starter. The JMS
provider client and JDBC driver remain consumer selections because there is no universally valid
vendor choice. Mobile never supplies a device, emulator, Appium server, application, credential,
or machine path.

## Compatibility

The nine starter coordinates are additive for 1.2.0. No 1.1.0 coordinate is removed, renamed, or
reinterpreted. Existing direct-module consumers remain supported and can adopt the BOM without
installing a starter. The generic messaging starter is intentionally non-operational: it supplies
the common contracts and shared infrastructure but no provider implementation, client, connection,
listener, or thread. Operational consumers select one or more provider starters and explicitly
enable and configure each required named instance. Web, API, database, and mobile are published by
PKG-120-002; the generic and provider-specific messaging POMs are published by PKG-120-003.
