# TestNG and Cucumber usage and separation

Use TestNG for technical verification: API/database checks, boundary cases, infrastructure
contracts, concurrency, and detailed failure diagnostics. Use Cucumber only for curated,
business-readable behavior whose scenarios express stable rules and acceptance language.

They are deliberately independent. Do not make a Cucumber runner inherit `TafBaseTest`, call one
runner from the other, share hook/listener lifecycle ownership, or force both through a common test
base. TestNG owns one `TestSession` per test-method invocation; framework Cucumber glue owns one
session per scenario. Application services and page/screen objects may be reused, but runner state
must not be.

The golden project demonstrates both paths:

```powershell
.\mvnw.cmd -pl demos/playwright-sauce-demo -Pfunctional-suite test
.\mvnw.cmd -pl demos/playwright-sauce-demo -Pbdd-suite test
```

```bash
./mvnw -pl demos/playwright-sauce-demo -Pfunctional-suite test
./mvnw -pl demos/playwright-sauce-demo -Pbdd-suite test
```

The functional suite selects TestNG tests; the BDD suite selects the Cucumber runner and glue.
Both resolve typed named controllers from their active session and close that session even after
failure. See the [Quick Start runner examples](../quick-start.md) for source-level walkthroughs.

