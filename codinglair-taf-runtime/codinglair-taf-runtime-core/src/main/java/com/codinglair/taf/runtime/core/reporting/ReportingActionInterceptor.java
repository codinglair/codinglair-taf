package com.codinglair.taf.runtime.core.reporting;

import com.codinglair.taf.runtime.core.reporting.abstraction.ReportLevel;
import com.codinglair.taf.runtime.core.reporting.annotation.ApiAction;
import com.codinglair.taf.runtime.core.reporting.annotation.BddStep;
import com.codinglair.taf.runtime.core.reporting.annotation.ComponentAction;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import com.codinglair.taf.runtime.core.reporting.annotation.PageAction;
import com.codinglair.taf.runtime.core.reporting.annotation.ScreenAction;
import com.codinglair.taf.runtime.core.reporting.annotation.Validation;
import com.codinglair.taf.runtime.core.reporting.annotation.Workflow;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.springframework.core.annotation.AnnotatedElementUtils;

/** Translates TAF-owned annotations into neutral invocation events. */
public final class ReportingActionInterceptor {
  private final CurrentReportingContext contexts;

  public ReportingActionInterceptor(CurrentReportingContext contexts) {
    this.contexts = contexts;
  }

  public Object invoke(Method method, Object[] arguments, Callable<Object> invocation)
      throws Exception {
    Action action = action(method);
    Optional<ReportingContext> current = contexts.current();
    if (action == null || current.isEmpty()) {
      return invocation.call();
    }
    if (action.level == ReportLevel.VALIDATION) {
      try {
        Object result = invocation.call();
        Object expected = arguments != null && arguments.length > 0 ? arguments[0] : "unspecified";
        Object actual = arguments != null && arguments.length > 1 ? arguments[1] : result;
        current.get().validation(action.name, "PASSED", expected, actual);
        return result;
      } catch (Throwable failure) {
        Object expected = arguments != null && arguments.length > 0 ? arguments[0] : "unspecified";
        Object actual =
            arguments != null && arguments.length > 1
                ? arguments[1]
                : failure.getClass().getSimpleName();
        current.get().validation(action.name, "FAILED", expected, actual);
        throwAny(failure);
        return null;
      }
    }
    return current.get().execute(action.level, action.name, invocation);
  }

  public static boolean isReportable(Method method) {
    return action(method) != null;
  }

  private static Action action(Method method) {
    Workflow workflow = AnnotatedElementUtils.findMergedAnnotation(method, Workflow.class);
    if (workflow != null) return new Action(ReportLevel.WORKFLOW, workflow.value());
    PageAction page = AnnotatedElementUtils.findMergedAnnotation(method, PageAction.class);
    if (page != null) return new Action(ReportLevel.PAGE, page.value());
    ComponentAction component =
        AnnotatedElementUtils.findMergedAnnotation(method, ComponentAction.class);
    if (component != null) return new Action(ReportLevel.COMPONENT, component.value());
    ApiAction api = AnnotatedElementUtils.findMergedAnnotation(method, ApiAction.class);
    if (api != null) return new Action(ReportLevel.API, api.value());
    ScreenAction screen = AnnotatedElementUtils.findMergedAnnotation(method, ScreenAction.class);
    if (screen != null) return new Action(ReportLevel.SCREEN, screen.value());
    BddStep bdd = AnnotatedElementUtils.findMergedAnnotation(method, BddStep.class);
    if (bdd != null) return new Action(ReportLevel.BDD_STEP, bdd.value());
    ControllerAction controller =
        AnnotatedElementUtils.findMergedAnnotation(method, ControllerAction.class);
    if (controller != null) return new Action(ReportLevel.CONTROLLER_OPERATION, controller.value());
    Validation validation = AnnotatedElementUtils.findMergedAnnotation(method, Validation.class);
    return validation == null ? null : new Action(ReportLevel.VALIDATION, validation.value());
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAny(Throwable failure) throws E {
    throw (E) failure;
  }

  private record Action(ReportLevel level, String name) {}
}
