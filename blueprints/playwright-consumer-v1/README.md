# Playwright consumer blueprint 1.0 (SAD v1.8)

This versioned blueprint generates the supported non-web Spring Boot, Playwright, CSV, and TestNG
consumer shape. Runtime owns sessions, preflight, reporting, and cleanup. Consumer code owns pages,
components, workflows, models, validators, and test intent.

Generate from the repository root:

```powershell
./blueprints/playwright-consumer-v1/generate.ps1 -Destination ./target/generated-playwright-consumer
```

Use `-IncludeBdd` to add the framework Cucumber runner, hooks-based glue, feature, and separate BDD
suite. Use `-IncludeAllure` to activate the optional Allure adapter dependency. The generator copies
the repository Maven Wrapper so the output is directly buildable against published or locally
installed <!-- taf-version -->`1.0.0` artifacts.

Blueprint schema 1.0 remains compatible: SAD v1.8 adds corrected examples and enforcement but does
not remove or reinterpret descriptor fields. Projects generated from the earlier example should
remove consumer lifecycle/reporting wrappers and service lookup, inject collaborators, centralize
immutable `LocatorSpec` fields in POM/PCOM owners, and separate prerequisite workflows from explicit
subject-under-test actions.
