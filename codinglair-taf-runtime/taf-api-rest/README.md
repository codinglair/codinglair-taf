# TAF REST API

Enable with `taf.api.rest.enabled=true` and configure either `taf.api.rest.base-url` or named
`taf.api.rest.controllers.<name>.base-url` values. Acquire `RestController` by type and name from the
active `TestSession`. Requests are immutable and reusable; every exchange emits a sanitized request
and response artifact. Transport and post-response validation failures add failure evidence without
changing the original REST result or assertion. Evidence is retained by the session
`ArtifactCollector` even when Allure is absent; use `testSession().getArtifactCollector()` to
retrieve it during the invocation. When Allure is installed, request/response/failure attachments
appear beneath a method-and-route operation step before the test lifecycle closes.

REST evidence includes the session/operation correlation, method, route template, sanitized named
parameters and headers, status, timing, content type, and a bounded body. Authorization and proxy
authorization, cookies, API keys, token/password/secret/credential fields, URL user information,
and similarly named query/path values are redacted case-insensitively. Text bodies are sanitized
before the `taf.api.rest.max-evidence-bytes` limit (default 65536) is applied. Binary/unsupported
bodies are explicitly omitted. The collector applies its mandatory second sanitization, hashing,
retention, and publication boundary. Never put secrets in path literals; use route templates and
named parameters.

Every REST capability call requires the invocation-bound `TestSession`; an unbound, stale, or
wrong-owner call fails with `TAF-LIFECYCLE-001` before transport. Parallel sessions have separate
collectors and operation sequences. A returned `nativeSpecification()` remains a vendor-native
escape hatch and must not be retained or used outside its owning invocation.

OpenAPI validation is provided through `RestContractValidator`; the concrete OpenAPI adapter and
scaffolding remain owned by CONTRACT-001.
