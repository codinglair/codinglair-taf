# TAF REST API

Enable with `taf.api.rest.enabled=true` and configure either `taf.api.rest.base-url` or named
`taf.api.rest.controllers.<name>.base-url` values. Acquire `RestController` by type and name from the
active `TestSession`. Requests are immutable and reusable; every exchange emits a sanitized request
and response artifact. `nativeSpecification()` is the capability-local REST Assured escape hatch.

OpenAPI validation is provided through `RestContractValidator`; the concrete OpenAPI adapter and
scaffolding remain owned by CONTRACT-001.
