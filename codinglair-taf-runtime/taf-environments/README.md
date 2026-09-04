# TAF consumer configuration and environment composition

Official Spring Boot consumers are non-web applications by default. Put application-owned
`@Configuration`, `@ConfigurationProperties`, adapters, and third-party beans below the consumer's
`config` package (for example, `com.example.automation.config`). Runtime modules are not modified for
customer-specific wiring.

The base profile declares named capability instances and safe functional defaults. Required values
remain explicit placeholders, and credentials remain opaque aliases:

```yaml
spring:
  main:
    web-application-type: none
taf:
  consumer:
    environment: ${TAF_ENVIRONMENT:local}
    capabilities:
      storefront:
        type: web-playwright
        required-values:
          base-url: ${STOREFRONT_URL:REPLACE_ME}
        secret-references:
          username: STOREFRONT_USERNAME
        enabled: true
```

`application-local.yaml` owns developer-safe overrides such as headless mode and timeouts.
`application-ci.yaml` owns CI execution mode and environment aliases. Neither profile contains secret
values. A scaffolded application context can therefore load before values exist. Immediately before
controlled environment execution, call `ConsumerPreflight.verify()`. It reports all unresolved
fields, missing secret aliases, incompatible selections, and contributed dependency-readiness
failures in one exception; it never includes resolved secret values.

Capability starters may contribute a `ConsumerPreflightContributor`. Disabled/unselected named
instances are not validated or activated. A consumer can replace `ConsumerPreflight` or
`SecretReferenceAvailability` with ordinary Spring beans.
