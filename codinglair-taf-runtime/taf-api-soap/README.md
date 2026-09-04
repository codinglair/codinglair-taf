# TAF SOAP API

Enable with `taf.api.soap.enabled=true` and configure `taf.api.soap.endpoint` or named `taf.api.soap.controllers.<name>.endpoint` values. Controllers are registered lazily in each `TestSession` as typed, named `SoapController` instances.

```properties
taf.api.soap.enabled=true
taf.api.soap.endpoint=https://service.example.test/soap
taf.api.soap.timeout=30s
taf.api.soap.max-response-bytes=10485760
```

The capability is disabled by default. No endpoint has a default. Timeout defaults to 30 seconds and maximum response size defaults to 10 MiB; named controllers inherit these defaults unless overridden.

The controller supports SOAP 1.1/1.2 envelopes, faults, XSD/XPath/comparison utilities, and multipart attachments. Evidence is sanitized by the session `ArtifactCollector`; binary attachment bodies are represented only by metadata and size.

## Generated clients

Typed clients are deliberately consumer-local. Configure `org.apache.cxf:cxf-codegen-plugin` `${cxf.version}` in `generate-sources`, place WSDL/XSD inputs in the consumer project, and emit into Maven's default `target/generated-sources/cxf`. Never commit generated output or move generated types into Runtime Core. Clean regeneration is a required consumer build gate.

WS-Security UsernameToken is available through `UsernameTokenSecurity`, using only `SecretManager` references. X.509 signature/encryption for generated clients uses CXF/WSS4J interceptors with `SecretReferenceCallbackHandler`; raw keys, passwords, tokens, and callback values must never appear in properties, logs, exceptions, or evidence.

Capability gaps in this slice: WS-Trust, WS-SecureConversation, WS-Federation, Kerberos, SAML issuance, and automatic WS-Policy negotiation.
