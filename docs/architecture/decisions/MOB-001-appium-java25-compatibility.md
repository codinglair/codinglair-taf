# MOB-001 Appium and Java 25 compatibility decision

- Appium Java Client `10.1.1` is pinned under the Apache-2.0 license.
- Selenium is pinned through `selenium-bom` `4.43.0`, a combination listed in Appium's official compatibility matrix. This avoids the client's open Selenium version range and reduces convergence risk.
- Appium 10 requires Java 11 or newer. MOB-001 compiles and runs its device-free suite on the repository Java 25 baseline; emulator/device protocol verification remains tiered and opt-in.
- Appium is confined to `taf-mobile-appium`; `taf-mobile-core` contains no Appium, Selenium, Spring, provisioning, Android, or iOS implementation dependency.
- Primary transitive risk is Selenium/Appium binary drift. Both sides are pinned and Maven verification is required for upgrades.
- Version 10.1.1 includes upstream server-URL security hardening. Endpoints are restricted to HTTP(S), resolved secrets are not capabilities, and controllers never spawn Appium processes.
