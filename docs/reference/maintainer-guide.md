# Maintainer guide

## Documentation and drift workflow

Update public behavior, generated Spring metadata, schemas, examples, reference prose, and
migration guidance in the same pull request. The root `pom.xml` `<revision>` property is the
authoritative Codinglair TAF version. GitHub-facing Markdown keeps resolved literal versions; the
`taf-version` HTML comment marks only the adjacent backtick-delimited value as script-managed.

Synchronize and validate those values with:

```text
java build-support/scripts/SyncDocVersion.java --write
java build-support/scripts/SyncDocVersion.java --check
```

Install the repository-managed pre-commit hook with
`java build-support/scripts/InstallGitHooks.java`, or manually run
`git config core.hooksPath .githooks`. The hook updates marked values but never stages them; when it
changes Markdown, it aborts the commit so the changes can be reviewed and staged. CI rejects
unsynchronized documentation.

Also run:

```text
./mvnw -Pdocs verify
./mvnw -Papi-compatibility,schema-compatibility verify
```

The `docs` Maven profile continues to run normal reactor verification. Run the dependency-free Java
source-file `--check` command alongside `-Pdocs`; GitHub, Jenkins, and GitLab pipelines invoke it
explicitly without requiring a separate scripting runtime.

The docs contract checks local links, balanced fences, released BOM artifact coverage,
`@ConfigurationProperties` prefix coverage, schema links, audience separation, and required
migration/version language. Java examples are compiled by their owning module or the standalone
Quick Start consumer POM. Release Javadocs provide source-generated member-level API reference.

## Compatibility and migration

Within a major release, preserve binary/source and schema compatibility. Deprecate for at least one
minor release and document the replacement before removal. Any unapproved public break requires an
ADR and explicit approval. Update the compatibility matrices and contract tests with every change.

The first public release establishes the compatibility baseline. Use one invocation-owned
`TestSession`, register typed and named controller factories with
`ControllerRegistry`/`TestSessionConfigurer`, acquire controllers lazily from the session, and
close the session.

## Maintainer completion checklist

- Build with Java 25 and the pinned Spring Boot 4.x baseline using the Maven Wrapper.
- Run narrow module tests, Spring condition tests, architecture/dependency checks, then required
  vertical and release gates.
- Confirm no internal fixtures enter published artifacts and no resolved secret enters output.
- Verify sources, Javadocs, schemas, SBOM, licenses, docs, compatibility, and migration guidance are
  packaged with the same version.
- Record exact verification commands and results in the pull request.
