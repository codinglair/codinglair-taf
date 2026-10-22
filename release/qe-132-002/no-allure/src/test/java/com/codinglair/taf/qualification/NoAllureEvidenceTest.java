package com.codinglair.taf.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.api.rest.RestController;
import com.codinglair.taf.api.rest.RestAutoConfiguration;
import com.codinglair.taf.api.rest.RestControllerFactory;
import com.codinglair.taf.api.rest.RestRequest;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import io.restassured.http.Method;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testng.annotations.Test;

public final class NoAllureEvidenceTest extends com.codinglair.taf.runtime.testng.TafBaseTest {
  @Test(description = "Retains sanitized collector evidence without Allure and rejects an unbound caller")
  public void retainsSanitizedEvidenceWithoutAllureAndRejectsNoSession() throws Exception {
    new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TafRuntimeAutoConfiguration.class, RestAutoConfiguration.class))
        .withPropertyValues("taf.api.rest.enabled=true", "taf.api.rest.base-url=http://127.0.0.1:1", "taf.api.rest.timeout=100ms")
        .run(context -> {
          testSession().getControllerRegistry().register(RestController.class, "no-allure", context.getBean(RestControllerFactory.class).create("default"));
          RestController controller = testSession().getController(RestController.class, "no-allure");
          assertThatThrownBy(() -> controller.execute(RestRequest.request(Method.GET, "/unavailable").queryParameter("token", "no-allure-secret").build())).isNotNull();
          assertThat(testSession().getArtifactCollector().getArtifacts()).hasSize(2).allSatisfy(artifact -> assertThat(artifact.content()).doesNotContain("no-allure-secret"));
          try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThatThrownBy(() -> executor.submit(() -> controller.execute(RestRequest.request(Method.GET, "/unavailable").build())).get())
                .rootCause().hasMessageContaining("TAF-LIFECYCLE-001");
          }
        });
  }
}
