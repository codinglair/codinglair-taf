# Operator guide and troubleshooting

## CI/CD and release

GitHub Actions is primary. Jenkins and GitLab invoke the same Maven gates. PR verification runs
unit, affected integration/contract, architecture, dependency, schema, API, docs, and relevant
vertical smoke checks. Nightly/release adds the full browser, database, messaging, container,
mobile, compatibility, security, cleanup, and Kind matrices.

Release artifacts include the BOM, binaries, sources, Javadocs, schemas, licenses, SBOM,
compatibility data, migration guidance, and this versioned reference. Follow
[release packaging](../operations/release-packaging.md), [nightly/release verification](../operations/nightly-and-release-verification.md),
and [Jenkins/GitLab pipelines](../operations/jenkins-and-gitlab-reference-pipelines.md).

### CI/CD examples

The executable GitHub Actions sources are the
[pull-request workflow](../../.github/workflows/pull-request.yml),
[verification matrix](../../.github/workflows/verification-matrix.yml),
[nightly workflow](../../.github/workflows/nightly.yml), and
[release workflow](../../.github/workflows/release.yml). They select Java 25 and invoke Maven-owned
gates. A minimal consumer job follows the same pattern:

```yaml
jobs:
  verify:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
          cache: maven
      - run: ./mvnw -B -ntp verify
```

Pin actions according to organizational policy and grant only `contents: read` unless a reviewed
job needs more. Jenkins and GitLab must call the same wrapper goals; do not duplicate or weaken the
gate logic in pipeline-specific scripts.

## Kind operations

The [Kind reference deployment](../operations/kind-reference-deployment.md) is functional and
incremental, not a production HA blueprint. Preserve non-root identities, probes, resource bounds,
separate control-plane/worker trust, persistence, and secret references. Never place secret values
in images, build arguments, manifests, logs, or artifacts.

## Troubleshooting sequence

1. Confirm Java 25, Maven Wrapper, artifact version, and the published compatibility matrix.
2. Run consumer preflight and fix every aggregated capability/environment diagnostic.
3. Confirm logical controller names match configuration and acquisition.
4. Distinguish product, automation, environment, test-data, flaky, requirement-ambiguity, and
   inconclusive failures before retrying.
5. Inspect bounded correlation IDs, job status, sanitized evidence, readiness, and audit metadata.
6. For hangs, verify configured timeout/cancellation and that descendants/resources were cleaned.
7. For MCP, validate the request against the v1 schema and compare STDIO/HTTP scope enforcement.
8. For containers, check provider readiness and dynamic port/resource ownership; do not provision
   infrastructure inside a controller.
