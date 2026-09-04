# Runtime Failure Classification and History

RT-012 makes `Error.ErrorType` the authoritative root-cause taxonomy. Root cause and historical
stability are independent: `FLAKY` remains for serialized compatibility, while new Runtime results
use `StabilityStatus.HISTORICALLY_FLAKY` without rewriting classification, outcome, or signature.

Classification precedence is: one approved explicit classification, deterministic boundary rule,
then `INCONCLUSIVE` with `UNCLASSIFIED` provenance. Contradictory explicit inputs fail. Assertion
failure alone is not a product defect. Reporter and vendor adapters only propagate the Runtime value.

## Failure signature v1

The format is `failure-signature:v1:<lowercase-sha256>`. UTF-8 canonical fields, in order, are
capability and phase followed by up to four configured cause-chain entries, each containing exception
type, normalized message, and up to the configured number of filtered class/method/file stack
locations. Suppressed exceptions are excluded in v1. Classification and test identity are excluded. Before hashing,
the Runtime redaction pipeline runs and timestamps, UUIDs, ports, temporary paths, memory addresses,
line numbers, path separators, and whitespace are normalized. Messages and frames are bounded. The
canonical preimage is never persisted.

## History

`ExecutionHistoryRepository` stores schema-v1 bounded attempt summaries only. The local provider
writes one JSON file per attempt using temporary-file plus atomic-move publication. Project and test
directories are SHA-256 identities, byte-equivalent duplicate execution/attempt pairs are idempotent,
conflicting reuse of an attempt identity is reported as corrupt, symbolic links
are not followed, and deterministic age/count/byte retention only deletes regular files under the
configured root. Corrupt records are excluded and optionally quarantined. Raw messages, stack traces,
payloads, evidence, inputs, outputs, requests, responses, screenshots, documents, logs, and secrets
are not history fields.

History outcomes distinguish `SUCCESS`, `DISABLED`, `UNAVAILABLE`, and `CORRUPT`. Optional history
failure does not change the current attempt. A compatible window is historically flaky only when its
minimum completed sample contains both pass and fail. Failure-only matching signatures indicate
recurrence, not flakiness. Skipped, aborted, and inconclusive attempts do not participate in the v1
stability calculation.

## Configuration

Properties use the `taf.history` prefix. Defaults are disabled, root
`target/taf-evidence/history`, 500 records, 30 days, 50 MiB, a 2,048-character signature message,
12 stack frames, four causes, two minimum completed samples, project `default`, build `unknown`,
environment `local`, continue-on-unavailability, and quarantine-on-corruption. Setting
`taf.history.enabled=true` activates the local provider. Invalid bounds fail during bean creation.

Runtime JSON/TestNG uses `failureAnalysis`; JUnit XML uses `taf.failure.*` properties; Allure uses
the same names as labels. Cucumber business results expose the same nested `FailureAnalysis`.
