package com.codinglair.taf.runtime.environment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

class FakeEnvironmentProviderTest extends EnvironmentProviderContract {
  private final AtomicInteger ids = new AtomicInteger();
  private final EnvironmentProvider provider =
      new AbstractEnvironmentProvider() {
        public String id() {
          return "fake";
        }

        public Set<EnvironmentMode> supportedModes() {
          return Set.of(EnvironmentMode.EXTERNAL);
        }

        public PreflightResult preflight(EnvironmentRequest request) {
          return PreflightResult.from(
              List.of(
                  new PreflightCheckResult(
                      "endpoint",
                      PreflightCheckType.ENDPOINT,
                      Optional.empty(),
                      EnvironmentStatus.READY,
                      "reachable",
                      "",
                      Map.of(),
                      Instant.now())));
        }

        protected EnvironmentResource create(EnvironmentRequest request) {
          return new TestResource(request.resourceName() + ids.incrementAndGet(), request.mode());
        }
      };

  @Override
  protected EnvironmentProvider provider() {
    return provider;
  }

  @Override
  protected int cleanupCount(EnvironmentResource resource) {
    return ((TestResource) resource).cleanupCalls();
  }
}
