package __BASE_PACKAGE__.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import __BASE_PACKAGE__.PlaywrightConsumerApplication;
import __BASE_PACKAGE__.configuration.ConsumerEnvironmentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("local")
@SpringBootTest(classes = PlaywrightConsumerApplication.class)
class LocalContextTest {
  @Autowired ConsumerEnvironmentProperties environment;

  @org.junit.jupiter.api.Test
  void intentionalLocalOverridesBind() {
    assertEquals("local", environment.name());
  }
}
