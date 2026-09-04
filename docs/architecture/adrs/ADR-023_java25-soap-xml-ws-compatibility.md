# ADR-021: Java 25 SOAP, XML, and WS compatibility

**Status:** Accepted for API-002  
**Date:** 2026-08-12

## Decision

Use JDK `HttpClient` and hardened JAXP for the deterministic envelope controller. Use Apache CXF 4.1.7 only for Jakarta JAX-WS contracts and reproducible build-time `wsdl2java` generation, and Apache WSS4J 4.0.1 for the WS-Security implementation boundary. Both projects have JDK 17 baselines and Jakarta APIs and are verified by API-002 on Java 25. Versions are pinned in the parent BOM/plugin management.

Generated clients are consumer/build artifacts under `target/generated-sources/cxf`; they are not Runtime Core contracts and are never checked into source. SOAP controller evidence passes through `ArtifactCollector`. XML processing disables DTDs and external entities. Credentials are accepted only as `SecretManager` references and resolved for the shortest deterministic operation.

## Supported baseline

- SOAP 1.1 and 1.2 over HTTP(S), document/literal envelopes, SOAP faults, XSD and XPath.
- MTOM/XOP-style multipart-related request and response attachments.
- WS-Security UsernameToken at the raw-envelope boundary; X.509 signature and encryption are supported through isolated CXF-generated clients configured with WSS4J and secret-backed callbacks.

WS-Trust, WS-SecureConversation, WS-Federation, Kerberos, SAML issuance, and policy negotiation are explicit capability gaps for this slice.
