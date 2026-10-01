# ADR-029: Distribute One MCP Image with STDIO and Streamable HTTP Profiles

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Decision:** Publish `codinglair/codinglair-taf-mcp` on Docker Hub with immutable release tags and optional `latest`. One image exposes mutually exclusive `stdio` and `streamable-http` runtime profiles. STDIO is supported when an MCP client launches `docker run --rm -i`; stdout is reserved for protocol messages and logs use stderr. Detached Docker, CI service, Kubernetes, and future hosted deployments use Streamable HTTP. Kubernetes is an officially supported target; attach/exec mechanisms are not supported MCP transports.

**Consequences:** One artifact covers local and remote use without duplicate images. The HTTP profile requires authentication/authorization, network policy, service exposure, probes, graceful shutdown, and persistent job/state design. STDIO has process-scoped health and client-owned lifecycle rather than an HTTP readiness endpoint.
