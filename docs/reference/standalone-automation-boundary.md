# Standalone automation setup and SUT boundary

Official generated projects are independent Maven builds. Set `automationRoot` to the directory
owned by test automation and `sutRoot` to the SUT build root. The legacy `destination` argument is
still accepted as an alias for `automationRoot`; new integrations should send the explicit field.

The generator canonicalizes the nearest existing ancestors, rejects traversal, links/junctions,
the SUT root, and nonempty directories that lack `src/test/resources/taf-project.json`. It repeats
containment checks immediately before each legacy-template write and revert. Existing authorization,
dependency approval, collision, provenance, and create-only behavior remain in force.

Generated descriptor version 1.1 declares:

- `project.type: standalone-test-automation`;
- `project.expectedBuildRoot: .`;
- `project.sutInteraction: external`;
- TestNG in `runners`.

Version 1.0 descriptors remain readable. A 1.1 descriptor is intentionally stricter and cannot use
the older `focused-golden`, `cross-capability`, or `consumer` project types.

## Protected scope

The SUT root supplied to generation and conformance is the explicit inspected scope. Protected
content includes ancestor/SUT POM and Gradle builds, dependency management, `src/main` production
source and configuration, deployment descriptors, and packaged JAR/WAR output. The automation
directory is excluded from SUT inspection but must not be registered in the SUT reactor.

`SutBoundaryVerifier.verify(sutRoot, automationRoot)` fails when the inspected SUT build references
Codinglair TAF, registers the automation directory, imports TAF from production source, or packages
TAF classes/artifacts. It does not certify files outside the supplied SUT root.

Typical safe layout:

```text
repository/
  sut/pom.xml
  test-automation/pom.xml
  test-automation/mvnw
  test-automation/mvnw.cmd
  test-automation/.mvn/maven.config
```

Boundary errors name the rejected condition, for example `automation root escapes the approved
root`, `automation root must not be the SUT build root`, or `symbolic-link scaffold path is not
allowed`. Move the automation root to a real directory beneath the approved workspace; do not add
the automation project to the SUT reactor as a workaround.
