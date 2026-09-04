# Introductory training session

## Outcome and duration

In 60–90 minutes, a new automation engineer should be able to explain Runtime versus MCP, run the
deterministic golden project, identify TestNG/Cucumber lifecycle ownership, configure a named
controller with secret references, and locate sanitized evidence and troubleshooting guidance.

## Walkthrough

1. **Prerequisites (10 minutes):** use the [installation guide](installation-and-prerequisites.md)
   to confirm Java 25 and the Maven Wrapper. Do not distribute credentials during training.
2. **Architecture (10 minutes):** trace `TestSession` → typed/named controller →
   `EnvironmentProvider` → `ArtifactCollector`; contrast direct Runtime use with governed MCP.
3. **Run (15 minutes):** execute
   `.\mvnw.cmd -pl demos/playwright-sauce-demo test` (or the POSIX wrapper). Confirm deterministic
   tests pass and external profiles remain opt-in.
4. **Inspect (15 minutes):** review the golden project's page/workflow, TestNG test, Cucumber
   feature/glue, `application.yaml`, and opaque secret references.
5. **Change safely (15 minutes):** add a local assertion or test definition, run the narrow test,
   and inspect structured failure classification. Revert the training-only edit afterward through
   the normal reviewable workflow; do not commit/push automatically.
6. **Operations (10 minutes):** locate CI gates, preflight diagnostics, sanitized evidence, job
   status, and cancellation. Discuss when human approval is mandatory.

Completion check: the participant can choose TestNG versus Cucumber, explain why a controller does
not provision infrastructure, name the secret-resolution boundary, and find the appropriate
reference without reading framework source.

