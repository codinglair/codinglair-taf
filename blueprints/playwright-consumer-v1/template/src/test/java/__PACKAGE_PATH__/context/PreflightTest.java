package __BASE_PACKAGE__.context;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.runtime.core.preflight.ConsumerPreflight;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightException;
import __BASE_PACKAGE__.PlaywrightConsumerApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = PlaywrightConsumerApplication.class)
class PreflightTest {
  @Autowired ConsumerPreflight preflight;

  @org.junit.jupiter.api.Test
  void unresolvedRequiredValuesFailBeforeBrowserStartup() {
    assertThrows(ConsumerPreflightException.class, preflight::verify);
  }
}
