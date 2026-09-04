# TAF Local Secret Providers

This optional Runtime capability supplies Spring-managed environment and Jasypt providers. It has no
Vault/cloud/Kubernetes substitutes. Runtime Core does not depend on this module or Jasypt.

Jasypt uses `PBEWITHHMACSHA512ANDAES_256`, random salt, random IV, and Base64 output. Re-encrypting the
same plaintext therefore produces different ciphertext. Jasypt is intended for local, air-gapped, and
legacy-compatible use; an approved enterprise secret store is preferred in managed environments.

Configure only the name of the bootstrap environment variable:

```yaml
taf:
  secrets:
    jasypt-master-key-environment-variable: ${TAF_JASYPT_MASTER_KEY_ENV:REPLACE_ME}
```

For local PowerShell setup, use placeholders and enter values interactively so neither value appears in
the command line:

```powershell
$env:TAF_JASYPT_MASTER_KEY_ENV = "TAF_JASYPT_MASTER_KEY"
$env:TAF_JASYPT_MASTER_KEY = Read-Host "Temporary local Jasypt master key"
```

Generate a separate output CSV without echoing the key, plaintext, or ciphertext. From the repository
root, after compiling `taf-secrets-local`, deliberately invoke:

```powershell
java -cp "codinglair-taf-runtime/taf-secrets-local/target/classes;codinglair-taf-runtime/taf-secrets-api/target/classes;$env:USERPROFILE/.m2/repository/org/jasypt/jasypt/1.9.3/jasypt-1.9.3.jar" `
  com.codinglair.taf.runtime.secret.tool.JasyptCsvReferenceGenerator `
  demos/playwright-sauce-demo/src/test/resources/test-data/inputs.csv `
  target/pwd-001/inputs.csv `
  TAF_JASYPT_MASTER_KEY SAUCE_DEMO_PASSWORD
```

The utility validates workspace-relative paths, refuses in-place overwrite, requires at least two
marked personas, and gives every row fresh random salt/IV. It prints only the replacement count. Review
and deliberately install the generated opaque references; never paste them into logs or prompts. For
the Sauce demo, also copy its non-sensitive `expected-outputs.csv` into `target/pwd-001`, then set
`TAF_TEST_DATA_LOCATION` to that directory. CI binds both environment variables through its
masked/secret variable facility.

```powershell
Copy-Item demos/playwright-sauce-demo/src/test/resources/test-data/expected-outputs.csv target/pwd-001/
$env:TAF_TEST_DATA_LOCATION = (Resolve-Path target/pwd-001).Path
```

To rotate, create a new external key, decrypt and immediately re-encrypt each credential inside the
authorized local boundary, replace every affected ciphertext, validate login paths, then revoke the old
key. `ENC(...)` is supported only by the deprecated common compatibility utility; migrate it to the
canonical `secret://jasypt/<payload>` form. Plaintext is never accepted as a fallback.

Failures identify malformed reference, denial, unavailable provider/bootstrap, or decryption failure
without including names, payloads, keys, or plaintext. Java and Playwright require a final `String`, so
the closeable holder can clear its own character buffer but cannot erase copies created by those APIs.

PWD-002 adds `JasyptSecretProvisioner` for explicit authoring operations. It uses the same algorithm,
random salt/IV, external bootstrap environment variable, and canonical grammar as the PWD-001
resolver. `JasyptCsvSecretNormalizer` can normalize an isolated workspace CSV copy in place; supply
only its path and the bootstrap environment-variable name. It prints metadata counts only and is
never invoked by ordinary repository reads or runner lifecycle hooks.
