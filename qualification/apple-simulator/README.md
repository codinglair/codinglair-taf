# Apple simulator qualification consumer

This standalone Maven project is the real VER-130-002 native, hybrid and Mobile Safari smoke. It
does not inherit the reactor parent and resolves candidate TAF artifacts from the repository staged
by `run-smoke.sh`. The fixture is synthetic source built for the selected simulator; no generated
application binary is committed.

Ordinary Maven `verify` compiles the consumer but skips its infrastructure-dependent tests. Only
the prepared-Mac runner activates them with `-Dtaf.apple.live=true`; missing live infrastructure
must then fail rather than skip.

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

The runner records prelaunch, readiness, pre-Appium, pre-Maven and periodic smoke-time WDA health
in `wda-lifecycle.log`, with bounded status, process, port-listener and launch-log evidence. A
supervisor records the `xcodebuild test-without-building` exit code without restarting WDA or
changing the smoke result. Appium 3.0.0 currently returns HTTP 404 for the runner's existing
`GET /sessions` cleanup check; that separate compatibility issue is intentionally not changed by
the WDA-lifetime diagnostics.

Simulator diagnostics retain the original `simctl bootstatus -b` output and elapsed time, then
record CoreSimulator state, the relevant device JSON, Simulator/CoreSimulator process state and WDA
reachability after boot, after WDA readiness, before Appium, before Maven and every five seconds
during the primary session attempt. A bounded macOS unified-log capture covers that session attempt.
These observations do not sleep, reboot, restart or otherwise alter the simulator. WDA termination
evidence labels cleanup-requested termination separately from an unexpected process exit.

After CoreSimulator boot readiness succeeds, the runner opens the `Simulator.app` bundled with the
configured Xcode path. It waits up to 30 seconds for that exact application process while requiring
the selected UDID to remain `Booted` on every poll. Failure messages distinguish CoreSimulator boot
state loss from Simulator UI startup failure; `simulator-ui-readiness.log` records the launch and
ready timestamps and UI PID. This prevents Appium from having to make a headless-but-booted
simulator visible during `POST /session`.
