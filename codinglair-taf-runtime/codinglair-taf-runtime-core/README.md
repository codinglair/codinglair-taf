# Session-aware consumer composition

Spring singleton beans may retain `SessionAwareAccessor`, configuration, and other stable
dependencies. They must resolve the active session/controller inside each factory method and must
not store the returned `TestSession`, `TestContext`, controller, page, component, API object,
screen, messaging workflow, or repository in singleton state.

An ordinary consumer extension can use standard Spring configuration:

```java
@Configuration(proxyBeanMethods = false)
class CustomerAutomationConfiguration {
    @Bean
    CheckoutPageFactory checkoutPageFactory(SessionAwareAccessor accessor) {
        return new CheckoutPageFactory(accessor);
    }

    @Bean
    CustomerSpecificClient customerSpecificClient(CustomerExtensionProperties properties) {
        return new CustomerSpecificClient(properties);
    }
}

final class CheckoutPageFactory {
    private final SessionAwareAccessor accessor;

    CheckoutPageFactory(SessionAwareAccessor accessor) {
        this.accessor = accessor;
    }

    CheckoutPage create() {
        return new CheckoutPage(accessor.controller(PlaywrightController.class, "storefront"));
    }
}
```

The factory is a singleton; each returned page is invocation-specific. The same rule applies to
POM/PCOM objects, API resources, mobile screens, messaging workflows, validators, and database
repositories. Calling the factory outside `TafBaseTest` or `TafCucumberHooks` execution fails
clearly and never opens a hidden session. Consumer-specific clients that do not use invocation
state remain ordinary Spring beans and require no Runtime modification.
