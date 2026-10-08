# ADR-001: Separate Runtime, MCP Server, and Quality Intelligence Products

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Context** The product vision combines deterministic automation, standardized agent access, and proprietary AI-assisted quality workflows. Treating them as one inseparable application would couple community adoption to AI, blur licensing boundaries, and prevent external agents from using the Runtime independently.

**Decision**

- Create three explicit product boundaries: TAF Runtime, TAF MCP Server, and Quality Intelligence Platform.
- TAF Runtime is deterministic and independently usable.
- TAF MCP Server depends on Runtime and is usable by standards-compliant external agents.
- Quality Intelligence consumes public Runtime/MCP contracts and may use external MCP servers.
- Community modules must not depend on proprietary modules.

**Consequences**

- Runtime and MCP can be adopted without the commercial platform.
- Licensing, packaging, dependency, and release boundaries become enforceable.
- Some contracts and integration tests must be maintained across products.
