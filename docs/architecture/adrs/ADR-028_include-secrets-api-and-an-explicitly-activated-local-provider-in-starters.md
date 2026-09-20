# ADR-028: Include Secrets API and an Explicitly Activated Local Provider in Starters

- **Status:** Accepted
- **Date:** 2026-09-14
- **Target release:** Codinglair TAF 1.2.0
- **Related requirements:** BR-012; SEC-006–SEC-009; FR-RT-013

## Context

Every capability needs secret references and redaction, while a local consumer needs a usable baseline. A default selected solely because an implementation happens to be on the classpath could silently weaken production behavior.

## Decision

Every capability starter includes the Secrets API and local provider implementation transitively. Availability is not activation. The local provider activates only through explicit configuration or the documented local profile and is never a silent production fallback.

Enterprise providers remain separately selectable. When multiple providers are enabled, configuration must select one provider or define explicit routing. Ambiguity fails startup validation; classpath order, bean order, and first-match behavior are prohibited.

Generated projects and examples contain secret references or unresolved placeholders only. Resolved values remain excluded from MCP, models, logs, reports, artifacts, image layers, and generated descriptors under ADR-011.

## Consequences

- Local onboarding does not require discovery of internal secret modules.
- Production behavior stays explicit and fail-closed.
- Context, preflight, and leak tests must cover provider ambiguity and local-profile activation.

## Alternatives considered

- Include only the API: rejected because the documented local baseline would require an additional unexplained dependency.
- Automatically prefer the local provider: rejected because it could mask intended enterprise configuration.
- Put secret-provider selection in each capability: rejected because secrets are cross-cutting project infrastructure.

