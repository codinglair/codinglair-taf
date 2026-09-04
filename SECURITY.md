# Security Policy

## Supported versions

| Version | Supported |
| --- | --- |
| <!-- taf-version -->`1.0.0` | Yes |
| Earlier versions | No |

## Reporting a vulnerability

Do not disclose an unresolved vulnerability in a public issue, discussion, pull request, commit message, or other public channel.

Open the repository's **Security** tab and select **Report a vulnerability** to submit a private vulnerability report. If that option is unavailable, do not disclose the vulnerability publicly. Repository maintainers must keep GitHub private vulnerability reporting enabled for this policy to provide a working intake channel.

A useful report should include:

- the affected component and build or release version;
- the vulnerability type and likely impact;
- prerequisites and reproducible steps or a minimal proof of concept;
- relevant configuration and environment details;
- suggested mitigations, if known; and
- whether the issue or related information has been disclosed elsewhere.

Remove secrets, personal data, private infrastructure details, and unrelated customer data from the report. Maintainers should acknowledge and assess reports as capacity permits, coordinate remediation and disclosure with the reporter, and communicate material status changes without promising a fixed response or resolution deadline.

## Scope

Reports are most useful when they concern code or configuration maintained in this repository. Vulnerabilities in a third-party dependency may still affect TAF, but should also be reported to that dependency's maintainer when appropriate. Security and availability of user-managed test environments, browsers, devices, databases, message brokers, identity providers, networks, credentials, and other infrastructure remain the user's responsibility unless the defect is caused by TAF.

Example values and test fixtures are not production credentials. Never reuse them in a real environment. Test automation can execute consequential actions against configured systems; users are responsible for isolation, least-privilege access, authorization, data handling, and cleanup in those systems.
