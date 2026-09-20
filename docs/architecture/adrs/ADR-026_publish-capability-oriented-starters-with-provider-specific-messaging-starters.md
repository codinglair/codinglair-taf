# ADR-026: Publish Capability-Oriented Starters with Provider-Specific Messaging Starters

- **Status:** Accepted
- **Date:** 2026-09-14
- **Target release:** Codinglair TAF 1.2.0
- **Related requirements:** BR-022–BR-027; FR-RT-012–FR-RT-017; NFR-019–NFR-021

## Context

Consumers currently encounter individual capability and infrastructure artifacts and must understand internal module topology. The public modular architecture must remain available, but normal installation must be capability-oriented. Messaging has several unrelated vendor stacks, so one all-provider dependency would impose unnecessary weight and conflict exposure.

## Decision

Publish `codinglair-taf-starter-web`, `codinglair-taf-starter-api`, `codinglair-taf-starter-database`, `codinglair-taf-starter-messaging`, and `codinglair-taf-starter-mobile` as Maven `pom` dependency aggregators with no implementation classes.

Each starter brings its documented baseline capability and shared Runtime, TestSession, environment, secrets, test-definition, lifecycle, reporting, evidence, structured-result, redaction, and TestNG infrastructure transitively. There is no required consumer-facing common starter. The BOM aligns all starter and module versions but installs no capability.

The generic messaging starter includes common messaging contracts, configuration, serialization/correlation support, bounded observation, evidence integration, and the provider SPI, but no concrete provider. Publish four provider starters:

- `codinglair-taf-starter-messaging-kafka`;
- `codinglair-taf-starter-messaging-rabbitmq`;
- `codinglair-taf-starter-messaging-jms`;
- `codinglair-taf-starter-messaging-aws` for EventBridge and SQS.

Each provider starter depends on the generic messaging starter and its implementation module. A normal consumer declares one or more provider starters; the common foundation resolves transitively once. A `MESSAGING` scaffold request without a provider fails before generation.

Provider presence does not activate a configured instance. Activation requires explicit enabled/configured instance properties and provider-specific conditional auto-configuration. An unconfigured provider creates no controller, client, connection, listener, thread, or environment resource.

Advanced consumers and extension authors may use the generic starter with a custom provider or supported direct modules.

## Consequences

- Consumers select capabilities rather than framework plumbing.
- Multiple starters share one compatible foundation through Maven mediation and the BOM.
- Unused provider SDKs do not enter the dependency graph.
- Provider selection is an additional explicit scaffold input.
- Combined-provider context tests, convergence checks, vulnerability scans, and conflict tests are release gates.
- Direct modules preserve fine-grained control.

## Alternatives considered

- One inclusive messaging starter: rejected because it forces all vendor stacks on every messaging consumer.
- Generic starter plus direct implementation module: rejected for normal consumers because it exposes internal module topology.
- Requiring both generic and provider starters explicitly: rejected because each provider starter can bring the generic foundation transitively.
- Merge implementation modules into starters: rejected because it would destroy module boundaries and selective consumption.
