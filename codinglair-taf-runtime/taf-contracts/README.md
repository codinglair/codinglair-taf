# TAF Contracts

`taf-contracts` provides deterministic, provider-neutral contract validation and in-memory
scaffolding. It never sends REST requests, publishes messages, or writes generated files.

The built-in adapters provide bounded structural validation for OpenAPI 3.x and AsyncAPI 2.x/3.x
JSON or YAML documents. Generated assets are versioned Java wrappers around the existing
`RestController` and `MessagingController`, so transport and lifecycle behavior remain owned by
their capability modules. Full semantic validation can be supplied through `ContractAdapter`.

Consumer contracts deliberately expose only `ConsumerContractAdapter`. Until a provider is
approved and registered, `UnsupportedConsumerContractAdapter` returns a structured capability gap
instead of claiming Pact or another provider is supported.

Inputs and generated assets carry separate schema and asset versions. Scaffolding returns confined
relative paths and content; the caller owns any authorized filesystem write.
