# ADR-027: Compose Projects from a Common Blueprint and Capability Contributions

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Decision:** Replace common Playwright assumptions with a common foundation plus Web, API, Database, Messaging, and Mobile contributions. Contributions declare file and configuration ownership, dependencies through starters, validation rules, and examples. Runner/reporting behavior is applied once at project level. MCP validates and orchestrates but does not own a second dependency graph.

**Consequences:** The current blueprint requires refactoring and new capability fragments. Generation becomes deterministic and extensible; collisions and unsupported combinations fail before files are written.
