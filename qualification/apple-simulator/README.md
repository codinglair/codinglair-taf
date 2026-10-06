# Apple simulator qualification consumer

This standalone Maven project is the real VER-130-002 native, hybrid and Mobile Safari smoke. It
does not inherit the reactor parent and resolves candidate TAF artifacts from the repository staged
by `run-smoke.sh`. The fixture is synthetic source built for the selected simulator; no generated
application binary is committed.

Run on a prepared Mac from the repository root:

```bash
bash qualification/apple-simulator/run-smoke.sh
```

Defaults select `macOS-15`, Xcode 16.4, iOS 18.5 and iPhone 16, with Java 25, Node 22.12.0,
Appium 3.0.0 and XCUITest 10.0.0. Override the documented `APPLE_*` variables only to create a new
qualification tuple; never treat an override as equivalent to the candidate record. Output is under
`target/ver-130-002`. Missing selected infrastructure fails setup; ordinary reactor builds do not
invoke this consumer.

The script creates, boots and deletes one job-owned simulator. It starts loopback-only Appium and a
loopback static page server, runs all three real cases, then runs one expected failing invocation and
requires zero remaining Appium sessions. Its trap attempts process and simulator cleanup on every
exit. Video and syslog are attempted and their controller-generated availability records are
retained; unsupported optional video is not a smoke failure. Screenshot and page source must be
nonempty.
