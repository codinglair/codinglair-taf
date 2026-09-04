# Community release dependency-license compliance record

This is the DEVOPS-005 engineering review of the 40 CycloneDX JSON SBOMs generated in
`target/staging-repository` and the corresponding resolved Maven dependency graph on 2026-08-30.
It records the distribution choice enforced by `ReleaseLicensePolicy`; it is not legal advice.

No listed third-party binary is shaded into a Codinglair JAR. Each is a Maven dependency referenced
by a published POM and is therefore included in the consumer-resolved distribution boundary. None
is a build plugin. `jakarta.jms-api` is direct; the other entries are transitive. Scope below is the
scope that makes the component reachable from at least one published community artifact; a
component may additionally occur only in test scope elsewhere in the reactor.

| Component | Graph status / scope | Raw SBOM declaration and supporting URL | Canonical choice | Class |
|---|---|---|---|---|
| `ch.qos.logback:logback-classic:1.5.34` | transitive / compile; not embedded | `EPL-2.0` (`https://www.eclipse.org/legal/epl-2.0`) or `LGPL-2.1-only` (`https://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html`) | `EPL-2.0` | Conditional |
| `ch.qos.logback:logback-core:1.5.34` | transitive / compile; not embedded | `EPL-2.0` (`https://www.eclipse.org/legal/epl-2.0`) or `LGPL-2.1-only` (`https://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html`) | `EPL-2.0` | Conditional |
| `com.github.spotbugs:spotbugs-annotations:4.9.8` | transitive / runtime; not embedded | `LGPL-2.1-only`; SBOM URL absent, exact version corroborated by artifact POM | `LGPL-2.1-only` | Conditional |
| `com.rabbitmq:amqp-client:5.30.0` | transitive / compile; not embedded | `AL 2.0` (`https://www.apache.org/licenses/LICENSE-2.0.html`), `GPL v2` (`https://www.gnu.org/licenses/gpl-2.0.txt`), or `MPL-2.0` (URL absent) | `Apache-2.0` | Permissive |
| `jakarta.annotation:jakarta.annotation-api:3.0.0` | transitive / compile; not embedded | `EPL-2.0` or `GPL-2.0-with-classpath-exception`; SBOM URLs absent | `EPL-2.0` | Conditional |
| `jakarta.jms:jakarta.jms-api:3.1.0` | direct / compile; not embedded | `EPL-2.0` (`https://www.eclipse.org/legal/epl-2.0`) or long-form GPL v2 with Classpath Exception (`https://projects.eclipse.org/license/secondary-gpl-2.0-cp`) | `EPL-2.0` | Conditional |
| `jakarta.mail:jakarta.mail-api:2.1.5` | transitive / compile; not embedded | `EPL-2.0`, `GPL-2.0-with-classpath-exception`, or `BSD-3-Clause`; SBOM URLs absent | `BSD-3-Clause` | Permissive |
| `net.java.dev.jna:jna:5.18.1` | transitive / compile; not embedded; also test-only paths | `LGPL-2.1-or-later` (`https://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html`) or `Apache-2.0` (`https://www.apache.org/licenses/LICENSE-2.0`) | `Apache-2.0` | Permissive |
| `org.antlr:antlr-runtime:3.5.3` | transitive / compile; not embedded | `BSD licence` (`http://antlr.org/license.html`); official text states the three-clause terms | `BSD-3-Clause` | Permissive, component-specific alias |
| `org.cryptacular:cryptacular:1.2.7` | transitive / compile; not embedded | `Apache-2.0` (URL absent) or generic `GNU Lesser General Public License` (`https://www.gnu.org/licenses/lgpl-3.0.txt`) | `Apache-2.0` | Permissive |
| `org.eclipse.angus:angus-mail:2.0.5` | transitive / compile; not embedded | `EPL-2.0`, `GPL-2.0-with-classpath-exception`, or `BSD-3-Clause`; SBOM URLs absent | `BSD-3-Clause` | Permissive |
| `wsdl4j:wsdl4j:1.6.3` | transitive / compile; not embedded; also test-only paths | `CPL` (`http://www.opensource.org/licenses/cpl1.0.txt`); artifact POM identifies Common Public License | `CPL-1.0` | Conditional, component-specific alias |

## Conditional approvals and operational evidence

The gate keys every approval by exact package URL including version. All six are independent,
unmodified Java libraries. Release evidence must retain applicable license and copyright notices;
source for any distributed modification must be made available under the applicable terms. Any
modification, shading, native-image linkage, or other packaging change requires a fresh formal
review before release.

| Exact components | Selected expression | Additional recorded obligation |
|---|---|---|
| Logback classic/core 1.5.34 | `EPL-2.0` | Preserve notices; publish source for distributed modifications. |
| SpotBugs annotations 4.9.8 | `LGPL-2.1-only` | Preserve replacement/relinking ability in addition to notice and modified-source duties. |
| Jakarta Annotations 3.0.0 | `EPL-2.0` | Preserve notices; publish source for distributed modifications. |
| Jakarta JMS 3.1.0 | `EPL-2.0` | The alternative Classpath Exception applies only to the identified artifact and is not generalized. |
| WSDL4J 1.6.3 | `CPL-1.0` | Make CPL-1.0 text and WSDL4J source availability visible on redistribution. |

## Enforcement result

`ReleaseLicensePolicyTest` covers exact GPL-with-Classpath normalization, rejection of plain GPL,
exact LGPL handling, generic LGPL/BSD/CPL rejection, permissive SPDX handling, and failure for
missing, unknown, prohibited, or conditionally unapproved declarations. On the staged inventory:

```text
Release license policy tests passed: 28
License policy passed: 40 SBOMs, 6 conditionally allowed components.
```

The pass applies only to this recorded inventory and packaging boundary. Promotion remains a
separately authorized human action.
