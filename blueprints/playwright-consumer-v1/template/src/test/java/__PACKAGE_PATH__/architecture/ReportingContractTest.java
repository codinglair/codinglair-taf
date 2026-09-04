package __BASE_PACKAGE__.architecture;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.codinglair.taf.runtime.core.reporting.annotation.PageAction;
import com.codinglair.taf.runtime.core.reporting.annotation.Workflow;
import __BASE_PACKAGE__.page.LoginPage;
import __BASE_PACKAGE__.service.LoginWorkflow;

class ReportingContractTest {
  @org.junit.jupiter.api.Test
  void meaningfulActionsUseNeutralTafAnnotations() throws Exception {
    assertNotNull(
        LoginPage.class
            .getMethod("login", String.class, String.class)
            .getAnnotation(PageAction.class));
    assertNotNull(
        LoginWorkflow.class
            .getMethod("authenticate", String.class, String.class)
            .getAnnotation(Workflow.class));
  }
}
