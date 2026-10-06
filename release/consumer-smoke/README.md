# External consumer conformance

These projects are Maven Invoker fixtures, not reactor modules. That isolation is intentional: each
fixture has no Codinglair parent, imports the public BOM, and resolves TAF artifacts from the staged
repository copied into its filtered POM by the root release gate.

From the repository root, first stage the release candidate and then run the one supported entry
point:

```shell
./mvnw clean deploy -Prelease-staging -DskipTests
./mvnw -B -ntp -N -Pconsumer-smoke verify
```

On Windows, use `mvnw.cmd`. If Java does not recognize the configured repository certificate, use
the operating-system trust store; do not disable TLS validation:

```powershell
$env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT'
.\mvnw.cmd -B -ntp -N -Pconsumer-smoke verify
```

To select one fixture while diagnosing a failure, add Maven Invoker's project selector, for example
`-Dinvoker.test=starter-api`. Do not run a source fixture POM directly because its `@taf.version@`
and `@taf.staging.repository@` tokens are populated by the root Invoker execution.

Each cloned fixture retains its full `build.log`, Surefire reports, dependency tree, and effective
POM under `target/consumer-smoke/<fixture>/`. The isolated Maven repository is
`target/consumer-repository`; the staged release repository is `target/staging-repository`.

The Apple release-candidate gate additionally compiles the standalone native/hybrid/Safari example
with its `taf-candidate` profile and a separate local repository. Run the complete local contract,
Android regression, consumer, and MCP image-profile gate with:

```powershell
pwsh -NoProfile -File build-support/verification/ver-130-001.ps1
```

Its manifest deliberately classifies Apple results as protocol/consumer evidence and leaves live
Apple qualification `UNVERIFIED`; `VER-130-002` owns real simulator qualification.
