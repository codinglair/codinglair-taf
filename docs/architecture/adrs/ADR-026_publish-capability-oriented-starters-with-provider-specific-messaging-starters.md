# ADR-026: Publish Capability-Oriented Starters with Provider-Specific Messaging Starters

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Decision:** Publish five top-level POM-packaged starters for Web, API, Database, Messaging, and Mobile. Shared infrastructure resolves transitively; no common starter is required. The generic messaging starter supplies common messaging infrastructure but no concrete provider. Kafka, RabbitMQ, JMS, and AWS provider starters each depend on it plus their implementation. A normal consumer selects one or more provider starters and never needs internal provider modules. The BOM governs compatible versions.

**Consequences:** Normal onboarding becomes capability-oriented and multiple starters compose through Maven mediation without forcing unused vendor stacks. Messaging requires a provider selection; scaffolding fails early when none is supplied. Combined-provider convergence and inactive-provider tests remain required.
