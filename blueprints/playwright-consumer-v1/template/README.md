# __ARTIFACT_ID__

Configure `SHOP_BASE_URL`, `TEST_ENVIRONMENT`, and the opaque `SHOP_USER_SECRET_REF`; never place
resolved credentials in YAML, CSV, logs, or reports. Edit paired `test-data/*.csv` rows and link each
test with `@TestCaseId`. Run deterministic checks with `./mvnw.cmd test`. Run the controlled browser
suite only with `./mvnw.cmd test -Pbrowser-smoke` after replacing required values. Functional and
BDD suites are separate (`testng-functional.xml`, `testng-bdd.xml`). Inspect neutral/optional Allure
output beneath `target`; browser evidence is controlled by Playwright evidence settings.
