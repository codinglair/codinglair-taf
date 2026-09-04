# Contributing to Codinglair TAF

Thank you for considering a contribution to the Codinglair Test Automation Framework (TAF).

## Before you start

Codinglair TAF requires Java 25. Use the checked-in Maven Wrapper; a separate Maven installation is not required. Git is also required. Some integration tests need additional software such as Docker, a browser, or an Android environment, but those tests should only be run when the affected capability requires them.

Clone your fork and create a focused branch from the current `staging` branch:

```bash
git clone https://github.com/codinglair-taf.git
cd taf
git checkout staging
git pull --ff-only origin staging
git checkout -b feature/short-description
```

On Windows PowerShell, use `./mvnw.cmd`; on POSIX shells, use `./mvnw`.

## Propose changes

Use [GitHub issues](https://github.com/codinglair-taf/issues) for reproducible bug reports and feature proposals. Search existing issues first. Include the affected module, expected and actual behavior, a minimal reproducer when possible, and relevant logs with credentials and other sensitive values removed.

Do not report suspected vulnerabilities in a public issue. Follow [SECURITY.md](SECURITY.md).

## Build and test

Run the narrowest commands that cover your change. Common POSIX commands are:

```bash
./mvnw -DskipITs test
./mvnw -pl path/to/affected-module -am verify
./mvnw spotless:check
./mvnw -Pdocs verify
./mvnw -Papi-compatibility,schema-compatibility verify
```

The PowerShell equivalents are:

```powershell
./mvnw.cmd -DskipITs test
./mvnw.cmd -pl path/to/affected-module -am verify
./mvnw.cmd spotless:check
./mvnw.cmd -Pdocs verify
./mvnw.cmd -Papi-compatibility,schema-compatibility verify
```

`test` runs unit tests. `verify` also runs lifecycle-bound integration and contract checks for the selected reactor. The compatibility profiles exercise repository API and schema gates. Documentation verification includes the repository's documentation checks. Environment-dependent browser, container, Kind, and Android checks are selected by CI or by their documented opt-in profiles; they are not prerequisites for every local change.

To apply Java formatting locally, run `./mvnw spotless:apply` (or `./mvnw.cmd spotless:apply` on PowerShell), then review the resulting diff.

## Change requirements

- Keep the Runtime independently usable without MCP or AI, and preserve module boundaries.
- Add or update unit tests for every changed production behavior. Add integration or contract tests for cross-module behavior, `ApplicationContextRunner` tests for Spring auto-configuration conditions, and concurrency or cleanup tests for scoped/shared resources when applicable.
- Update public documentation and [CHANGELOG.md](CHANGELOG.md) for notable user-visible changes. Do not add raw commit or pull-request entries.
- Keep TestNG and Cucumber integrations independent. Controllers must not provision infrastructure, and controller evidence must go through the framework artifact-collection boundary.
- Do not commit credentials, tokens, private endpoints, customer data, or sensitive values in code, fixtures, logs, screenshots, prompts, or artifacts.
- Treat public APIs, configuration keys, serialized schemas, and documented behavior as compatibility surfaces. Clearly identify any proposed breaking change and provide migration guidance. Maintainer review is required before accepting one.

### Dependencies

Before adding or upgrading a dependency, check whether the JDK, Spring, or an existing dependency already provides the capability. In the pull request, document the dependency's purpose, exact version, license, important transitive dependencies, and security impact. Dependency changes require explicit maintainer approval and must preserve the versions managed by the parent POM/BOM and the configured Maven repository or mirror policy.

## Pull requests

Open contributor pull requests against `staging`, not `master`. Explain the problem and solution, link related issues, list the commands actually run and their results, and call out compatibility, security, documentation, and dependency effects. Do not claim an environment-dependent check passed unless you ran it successfully.

The repository's pull-request workflows select documentation, unit, affected-module, compatibility, architecture, cross-module, browser, mobile, container, and deployment checks according to the change. A pull request is ready to merge only after its required checks and review are complete. Maintainers may request additional verification for higher-risk changes.

`master` is the release branch and does not accept ordinary contribution pull requests or direct pushes. Maintainers promote a verified `staging` state through a separate pull request from `staging` to `master`. That promotion pull request is subject to the release verification and approval requirements configured for `master`; releases are tagged from the resulting `master` commit.

Urgent release fixes may use a maintainer-controlled `hotfix/*` pull request into `master`. After such a fix is merged, maintainers must merge the resulting `master` changes back into `staging` before accepting further promotion work.

Contributors may use AI-assisted tools, but remain responsible for correctness, licensing, security, tests, and review of every submitted change.

## License

By submitting a contribution, you agree that it may be distributed under the repository's [Apache License 2.0](LICENSE).
