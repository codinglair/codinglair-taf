package __BASE_PACKAGE__.bdd;

import com.codinglair.taf.runtime.cucumber.TafCucumberHooks;
import org.testng.Assert;
import org.testng.annotations.Test;

public final class AppleCompositionTest {
  @Test
  public void composesIndependentScenarioGlueAndReleasesContext() {
    var factory = new AppleObjectFactory();
    Assert.assertTrue(factory.addClass(AppleSteps.class));
    Assert.assertTrue(factory.addClass(TafCucumberHooks.class));
    factory.start();
    try {
      Assert.assertNotNull(factory.getInstance(TafCucumberHooks.class));
      Assert.assertNotNull(factory.getInstance(AppleSteps.class));
    } finally { factory.stop(); }
    factory.stop();
    Assert.expectThrows(IllegalArgumentException.class, () -> factory.getInstance(AppleSteps.class));
  }
}
