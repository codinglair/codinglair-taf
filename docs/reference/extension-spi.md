# Extension-author guide

## Add a controller capability

1. Create a capability-specific optional module depending on Runtime Core, never MCP or another
   technology adapter unless the contract requires it.
2. Implement `TestController`: stable identity, explicit state, lazy `initialize`, structured
   health, sanitized `collectArtifacts`, and idempotent `close`.
3. Add typed settings and `@ConfigurationProperties`; validate names, timeouts, sizes, paths, and
   secret references at the boundary. Add Spring configuration metadata.
4. Supply a `TestSessionConfigurer` that registers factories by controller type and logical name.
   Auto-configuration must be conditional, back off for consumer beans, and avoid cross-module
   scanning.
5. Put provisioning in an `EnvironmentProvider`; pass only a resource description through the
   controller context.
6. Add reporter-neutral action annotations only to meaningful public operations and publish
   evidence through `ArtifactCollector`.
7. Test lifecycle transitions, named instances, lazy initialization, concurrency/isolation,
   reverse/idempotent cleanup, partial initialization, cancellation, sanitization, Spring
   conditions, and architecture boundaries.
8. Add the artifact and capability to this reference and run `./mvnw -Pdocs verify`.

## Add a provider

Implement the narrow existing SPI (environment, reporter, definition repository, secret resolver,
or other approved boundary). Keep vendor types inside the adapter, define deterministic ownership
and cleanup, preserve interruption, and return structured failures with corrective action. Reuse
the provider contract test kit where one exists; add real-boundary integration tests with isolated
resources and dynamic ports. Provider absence must not break unrelated modules.

No extension may fabricate a missing Runtime capability, expose resolved secrets, introduce global
mutable session state, or make Community modules depend on proprietary code.

