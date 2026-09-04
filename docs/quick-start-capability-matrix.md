# DOC-001 Release Capability and Documentation Matrix

This inventory is derived from `codinglair-taf-bom/pom.xml` for the root POM <!-- taf-version -->`1.0.0`. “Released” means
BOM-managed and publishable; it does not mean a complete DOC-001 consumer example exists. MCP uses
governed project/job workflows and does not expose each Runtime controller as a low-level tool.

| Artifact | Disposition | Direct Runtime / enablement | MCP-assisted disposition | Verification and limits |
| --- | --- | --- | --- | --- |
| `codinglair-taf-common` | Internal support | Transitive values; not a capability | None | Not a user workflow |
| `codinglair-taf-runtime-core` | Required consumer API | `TestSession`; [README](../codinglair-taf-runtime/codinglair-taf-runtime-core/README.md) | Validate/build/execute jobs | Clean runtime smoke; one session per method/scenario |
| `taf-secrets-api` | Consumer SPI | Opaque references; [README](../codinglair-taf-runtime/taf-secrets-api/README.md) | References only | Values never enter MCP |
| `taf-secrets-local` | Optional provider | [README](../codinglair-taf-runtime/taf-secrets-local/README.md) | No value retrieval | Local only; full consumer example is a gap |
| `taf-test-definitions` | Consumer capability | JSON/CSV; [README](../codinglair-taf-runtime/taf-test-definitions/README.md) | Project validation | SauceDemo; YAML/negative example is a gap |
| `taf-test-definitions-mongodb` | Optional provider | [README](../codinglair-taf-runtime/taf-test-definitions-mongodb/README.md) | No raw DB tool | Container test; clean example is a gap |
| `taf-file` | Optional capability | [README](../codinglair-taf-runtime/taf-file/README.md) | No raw filesystem tool | Root/size bounds; golden scenario is a gap |
| `taf-web-playwright` | Optional capability | [SauceDemo](../demos/playwright-sauce-demo/README.md) | Execute project tests | Browser opt-in; no raw browser tool |
| `taf-mobile-core` | Provider SPI | Selected through provider | No device tool | Not independently useful |
| `taf-mobile-appium` | Optional capability | [README](../codinglair-taf-runtime/taf-mobile-appium/README.md) | Execute project tests | Device opt-in; golden scenario is a gap |
| `taf-api-rest` | Optional capability | [README](../codinglair-taf-runtime/taf-api-rest/README.md) | Execute Java tests | WireMock tests; clean CRUD/negative example is a gap |
| `taf-api-soap` | Optional capability | [README](../codinglair-taf-runtime/taf-api-soap/README.md) | Execute Java tests | Clean fault/auth example is a gap |
| `taf-virtualization-wiremock` | Optional provider | [README](../codinglair-taf-runtime/taf-virtualization-wiremock/README.md) | No raw mock tool | Test-only; golden scenario is a gap |
| `taf-contracts` | Optional capability | [README](../codinglair-taf-runtime/taf-contracts/README.md) | Validate/build jobs | Clean OpenAPI/AsyncAPI example is a gap |
| `taf-messaging-core` | Provider SPI | Adapter-neutral contracts | No broker tool | Select a native adapter |
| `taf-messaging-kafka` | Optional adapter | Public adapter API | Execute Java tests | Container tests; golden scenario is a gap |
| `taf-messaging-rabbitmq` | Optional adapter | Public adapter API | Execute Java tests | Container tests; golden scenario is a gap |
| `taf-messaging-jms` | Optional adapter | Public JMS API | Execute Java tests | Provider tests; golden scenario is a gap |
| `taf-environments` | Optional provider | [README](../codinglair-taf-runtime/taf-environments/README.md) | Validate/execute environment | Dynamic ports/cleanup; golden scenario is a gap |
| `taf-database` | Optional capability | [README](../codinglair-taf-runtime/taf-database/README.md) | No raw SQL tool | Read-only preferred; scalar/row/list/F2B example is a gap |
| `taf-observability` | Optional capability | Public assertion API | Execute Java tests | Clean consumer example is a gap |
| `taf-data-migration` | Optional setup | [README](../codinglair-taf-runtime/taf-data-migration/README.md) | Policy/approval applies | Non-production; seed/rollback example is a gap |
| `codinglair-taf-reporting-allure` | Optional adapter | Runner evidence/report | Retrieve evidence/report | Publishing consumer example is a gap |
| `codinglair-taf-runner-testng` | Consumer runner | Extend `TafBaseTest` | Execute suite | SauceDemo; one session per method |
| `codinglair-taf-runner-cucumber` | Optional runner | [README](../codinglair-taf-runtime/codinglair-taf-runner-cucumber/README.md) | Execute tags/suite | SauceDemo; one session per scenario |
| `taf-consumer-conformance` | Build support | [README](../codinglair-taf-runtime/taf-consumer-conformance/README.md) | Validate project | Not a controller |
| `codinglair-taf-mcp` | MCP aggregate | Not a Runtime capability | Aggregate dependency | Server-side only |
| `taf-mcp-contracts` | MCP support | Versioned schemas | Governs operations | Contract tests |
| `taf-mcp-jobs` | MCP support | Job state machine | Status/timeout/idempotency/cancel | No capability-level access |
| `taf-mcp-security` | MCP support | OIDC/RBAC/approval | Enforces authorization | Never disable |
| `taf-execution-worker` | MCP support | Isolated execution | Build/execute/cancel | Restricted commands/workspace |
| `taf-mcp-resources` | MCP support | Controlled resources | Discover/retrieve | Bounded/sanitized |
| `taf-mcp-tools` | MCP support | Governed tools | Discover/validate/scaffold/build/execute/inspect/retrieve/cancel/report | No raw shell/browser/SQL/filesystem |
| `taf-mcp-prompts` | MCP support | Curated prompts | Discover/use prompts | Output remains untrusted |
| `taf-mcp-transport-stdio` | MCP transport | External process | Same governed surface | External-client smoke; logs on stderr |
| `taf-mcp-transport-http` | MCP transport | OAuth/OIDC HTTP | Same governed surface | Authenticated smoke; audience/scope enforced |

## Not available in this release

- Proprietary Quality Intelligence and QA Agent capabilities.
- A public AI-system testing controller.
- One comprehensive golden consumer spanning every released capability.

Rows marked as gaps are release-readiness defects for DOC-001. Framework tests prove product
behavior but do not replace positive and negative clean-consumer onboarding workflows.
