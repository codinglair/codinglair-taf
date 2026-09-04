# TAF WireMock service virtualization

Provision a `wiremock` environment through `SessionEnvironmentManager`, then create a session-owned mapping scope with `WireMockVirtualizationFactory`. The same mapping and verification code consumes the resource's `taf.virtualization.wiremock.base-url` in external and container modes. All request paths are prefixed with a sanitized session namespace and mappings/request-journal entries are removed when the session closes.

Network connection faults are denied by default and remain denied for `prod`/`production` even when the Spring opt-in is enabled. Latency simulation is non-disruptive and does not require that opt-in.
