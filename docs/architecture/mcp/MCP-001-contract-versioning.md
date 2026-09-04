# MCP contract versioning

MCP contracts are transport-neutral and independently versioned from the Maven artifact. Version 1.0 is published under `META-INF/taf/mcp/schema/v1` and identified by stable `urn:codinglair:taf:mcp:schema:v1:*` identifiers. STDIO and Streamable HTTP adapters must expose schema-equivalent payloads.

Within major version 1, additive optional properties and additive catalog entries are backward compatible. Removing or renaming a property, making an optional property required, narrowing an accepted value/range, changing an operation from synchronous to asynchronous, or strengthening permission/approval/side-effect semantics is breaking and requires a new major schema path plus migration guidance. Security may be tightened in place only to reject data that was already prohibited, such as resolved secrets or workspace-escaping paths.

Clients must send `schemaVersion`; servers reject unsupported major versions with `TAF-MCP-0001` (`validation`). Catalog versions use semantic versioning and are independent of the Maven artifact version. The catalog published in the v1 baseline is immutably identified as `1.0.0`; a catalog change must update its version and compatibility fixtures deliberately. Opaque cursors are scoped to the caller, query, and authorization decision and must not be fabricated, logged as credentials, or reused after expiry. Page sizes are capped at 100. Responses and inline resources are bounded; larger output is returned through controlled `taf://` references.

Errors use stable `TAF-MCP-NNNN` codes, a category, sanitized actionable message, retryability, and correlation ID. Error details must not contain secret values, raw worker output, authorization policy internals, or sensitive target data.

Compatibility fixtures in `taf-mcp-contracts/src/test/resources/fixtures/v1` are part of the published compatibility baseline. Later schema changes must retain all valid fixtures for the supported major version and retain negative fixtures as rejected inputs.
