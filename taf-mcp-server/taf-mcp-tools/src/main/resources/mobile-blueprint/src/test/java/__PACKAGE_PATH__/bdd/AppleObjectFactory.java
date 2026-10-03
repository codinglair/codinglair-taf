package __BASE_PACKAGE__.bdd;

import com.codinglair.taf.runtime.core.lifecycle.TestSessionLifecycle;
import com.codinglair.taf.runtime.core.reporting.CurrentReportingContext;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.cucumber.CucumberScenarioSession;
import com.codinglair.taf.runtime.cucumber.TafCucumberHooks;
import __BASE_PACKAGE__.configuration.MobileConfiguration;
import io.cucumber.core.backend.ObjectFactory;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Scenario-owned Spring composition bridge; TAF hooks remain the sole session lifecycle owner. */
public final class AppleObjectFactory implements ObjectFactory {
  private AnnotationConfigApplicationContext context;
  private final Map<Class<?>, Object> instances = new HashMap<>();

  @Override public boolean addClass(Class<?> type) {
    return type == AppleSteps.class || type == TafCucumberHooks.class;
  }

  @Override public void start() {
    context = new AnnotationConfigApplicationContext();
    try {
      context.getEnvironment().setActiveProfiles("taf-local");
      new MobileConfiguration.Initializer().initialize(context);
      context.register(MobileConfiguration.class);
      context.refresh();
      var scenario = new CucumberScenarioSession(context.getBean(TestSessionLifecycle.class),
          context.getBean(CurrentReportingContext.class), context.getBeansOfType(TestReporter.class).values().stream().toList());
      instances.put(TafCucumberHooks.class, new TafCucumberHooks(scenario));
      instances.put(AppleSteps.class, new AppleSteps(scenario, context));
    } catch (RuntimeException | Error failure) {
      stop();
      throw failure;
    }
  }

  @Override public void stop() {
    instances.clear();
    if (context != null) { context.close(); context = null; }
  }

  @Override public <T> T getInstance(Class<T> type) {
    Object instance = instances.get(type);
    if (instance == null) throw new IllegalArgumentException("Unregistered Apple scenario glue");
    return type.cast(instance);
  }
}
