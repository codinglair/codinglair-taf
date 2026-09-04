# TAF Cucumber runner

Create a small runner for each curated business suite:

```java
@CucumberOptions(
    features = "classpath:features/account_balance.feature",
    glue = {"com.example.acceptance", "com.codinglair.taf.runtime.cucumber"},
    plugin = "com.codinglair.taf.runtime.cucumber.CucumberBusinessReportPlugin")
public final class AccountAcceptanceRunner extends AbstractCucumberRunner {}
```

The framework glue registers `TafCucumberHooks`; do not copy or recreate those hooks in the
consumer project. Add one `@test-case-<id>` tag when a scenario needs a stable external test-case
identifier. Step definitions can resolve it through `CucumberScenarioSession.testCaseId()`.

Reference runner classes from separate TestNG XML files. Select an XML suite dynamically from a
workstation or CI job without editing the project POM:

```shell
./mvnw test -Dsurefire.suiteXmlFiles=src/test/resources/suites/account-acceptance.xml
```

Keep technical TestNG classes in technical XML suites and Cucumber runners in BDD XML suites when
physically separate report output is required. Ordinary TestNG tests must not inherit from
`AbstractCucumberRunner` or use Cucumber glue.
