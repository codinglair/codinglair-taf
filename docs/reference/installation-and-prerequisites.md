# Installation and prerequisites

## Required local tools

- A Java 25 JDK. Confirm both `java -version` and `javac -version` select that JDK.
- Git with authorized read access to this repository.
- The checked-in Maven Wrapper (`mvnw` or `mvnw.cmd`). A separately installed Maven is not needed.
- Access to the organization-approved Maven mirror or staged repository. Preserve the supplied
  `settings.xml`, proxy, and mirror configuration; do not bypass it to contact Maven Central.

Clone through the repository URL and credentials provided by the maintainer, then run from the
repository root:

```powershell
.\mvnw.cmd -version
.\mvnw.cmd -Pdocs verify
```

```bash
./mvnw -version
./mvnw -Pdocs verify
```

The version output must report Java 25. The docs gate verifies the complete deterministic reactor;
it does not authorize external systems or production access.

## Optional capability prerequisites

| Capability | Additional prerequisite |
| --- | --- |
| Testcontainers, databases, brokers, WireMock | Supported Docker-compatible daemon; permission to pull only approved images; sufficient disk/memory |
| Playwright live browser | Authorized SUT URL, approved browser binaries, and opaque credential references |
| Android/Appium | Android SDK/platform tools, approved emulator image or authorized device, Appium 3 and UiAutomator2; see the [mobile guide](android-appium-setup.md) |
| External database or broker | Network route, least-privilege test identity, isolated namespace, and cleanup authority |
| MCP Streamable HTTP | Trusted issuer metadata and authorized OIDC audience/scope configuration |

Never put a resolved credential in source, YAML, commands, reports, screenshots, or artifacts.
Confirm readiness with consumer preflight before a live run. For containers, verify the daemon with
the organization-approved command and then run only the affected opt-in integration profile. For
an external environment, verify DNS/TLS/connectivity using approved operational tooling; TAF
controllers must not provision it.

Start consumer-project setup with the [Quick Start](../quick-start.md). Java/Spring/ecosystem
versions are controlled by the [compatibility matrix](../engineering/compatibility-matrix.md).

