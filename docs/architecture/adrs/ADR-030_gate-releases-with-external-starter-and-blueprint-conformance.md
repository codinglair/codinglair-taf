# ADR-030: Gate Releases with External Starter and Blueprint Conformance

**Source:** Inherited decision text from SAD 1.12 as carried into SAD 1.13. Historical status/date/owners are not supplied here.

**Decision:** Extend consumer conformance to test every top-level starter individually, every messaging provider starter individually, all five top-level starters plus all messaging providers together, one generated Web + API + Database project, and supported direct-module use outside the reactor. Validate published Maven examples and the supported Gradle dependency syntax. Do not generate Gradle projects in release 1.2.0 and do not exhaustively test all top-level starter combinations without identified interaction risk.

**Consequences:** Publication and documentation errors become release failures. Individual and maximal graphs catch complementary defects while keeping the matrix bounded.
