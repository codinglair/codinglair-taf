# AWS messaging staged-artifact consumer

This project is intentionally outside the TAF Maven reactor. It imports the public TAF BOM and
resolves only staged or released artifacts. The smoke test starts LocalStack through TAF's
environment-provider boundary, uses dynamically mapped ports, acquires named SQS and EventBridge
controllers from one `TestSession`, and verifies routing, schema, attributes, negative matching,
sanitized Allure evidence, acknowledgment, and owned-resource cleanup.

From the repository root, run both commands below, in order and in the same checkout. The first
command creates `target/staging-repository` and must succeed before the consumer command runs. The
consumer command intentionally fails rather than resolving TAF artifacts from the reactor or the
developer's local Maven repository. Docker must be available.

Windows PowerShell:

```powershell
.\mvnw.cmd clean deploy -Drevision=1.1.0 -Prelease-staging
.\mvnw.cmd -N -Drevision=1.1.0 -Pconsumer-smoke verify
```

POSIX shell:

```sh
./mvnw clean deploy -Drevision=1.1.0 -Prelease-staging
./mvnw -N -Drevision=1.1.0 -Pconsumer-smoke verify
```

Release verification normally runs all projects below `release/consumer-smoke` through the root
`consumer-smoke` profile. Its Maven Invoker clone and isolated local repository are the
authoritative clean-cache/no-reactor-dependency proof. Running only the second command in a clean
checkout fails because no staged BOM exists; the profile reports the missing prerequisite during
Maven's `validate` phase.
