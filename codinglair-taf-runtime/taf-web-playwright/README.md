# TAF Web Playwright

This optional capability module registers one lazy, invocation-owned `PlaywrightController` for
each configured name. Runtime Core remains usable without this module and never imports Playwright.

```yaml
taf:
  consumer:
    capabilities:
      customer-web:
        type: web-playwright
  web:
    playwright:
      enabled: true
      controllers:
        customer-web:
          base-url: https://example.test/
          engine: chromium
          headless: true
```

Call `ConsumerPreflight.verify()` at the controlled execution boundary. Invalid URLs, remote-mode
selection without a WebSocket endpoint, and incompatible browser/channel choices are aggregated
before a browser starts. Endpoint values are not included in diagnostics.

Tests extending `TafBaseTest` obtain the named controller from the active session:

```java
PlaywrightController web = controller(PlaywrightController.class, "customer-web");
```

No controller should be constructed by consumer tests. Spring-managed page and component factories
may retain the singleton-safe `PlaywrightObjectFactory`; each `page` or `component` call resolves the
controller belonging to the active invocation. Returned page/component objects remain
invocation-owned and must not be cached in singleton fields.

Controller operations use TAF's neutral `@ControllerAction` level. Failure evidence flows only
through the session `ArtifactCollector`; text is sanitized and visual evidence requires explicit
publication authorization.
