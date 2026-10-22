# Boot process-boundary fixtures

These are standalone SUT builds, not reactor modules and not TAF consumers. `boot3` deliberately
uses Java 17 bytecode and Spring Boot 3; `boot4` uses Java 25 and Spring Boot 4. The VER-132-001
wrapper records protected-file and dependency-tree hashes before and after automation generation,
builds each SUT independently, rejects TAF coordinates/classes or automation output in its JAR,
and starts each application in a separate process for external REST qualification.
