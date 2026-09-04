# TAF Secrets API

This mandatory, provider-neutral Runtime module owns the execution-boundary contracts. `SecretReference`
parses version-one CSV-safe references; `SecretProvider` is a backend SPI; `SecretManager` is the single
authorized orchestrator; `ResolvedSecret` is a short-lived closeable holder; and audit events contain
metadata only.

Canonical grammar:

- `secret://env/<UPPER_CASE_VARIABLE_NAME>`
- `secret://jasypt/<base64-or-base64url-ciphertext>`
- `credential://<lower-case-profile-alias>` (reserved until an approved profile provider exists)

The `taf-test-definitions` `SecretReference` remains an opaque persistence-layer value. It deliberately
does not parse or resolve. This module parses that value only at deterministic execution time.
