package com.codinglair.taf.mcp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codinglair.taf.mcp.security.CallerIdentity;
import com.codinglair.taf.mcp.security.IdentityKind;
import com.codinglair.taf.mcp.security.Transport;
import com.codinglair.taf.mcp.tools.BlueprintScaffoldRequest;
import com.codinglair.taf.mcp.tools.BlueprintScaffoldingService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HttpScaffoldingBoundaryAdapterTest {
  @Test
  void forwardsDistinctAutomationAndSutRootsToTheSharedBoundaryService() {
    var service = mock(BlueprintScaffoldingService.class);
    var caller = mock(HttpCallerContext.class);
    when(caller.project()).thenReturn("project");
    when(caller.environment()).thenReturn("local");
    when(caller.identity())
        .thenReturn(new CallerIdentity("user", Set.of("developer"), "agent", IdentityKind.AGENT));
    var arguments = blueprintArguments();
    arguments.put("automationRoot", "automation");
    arguments.put("sutRoot", "sut");

    new HttpScaffoldingTools(service, caller)
        .scaffold(
            new HttpWorkflowRequest(
                "1.0", "request", "scaffold", 30, Optional.empty(), Optional.empty(), arguments));

    var captured = ArgumentCaptor.forClass(BlueprintScaffoldRequest.class);
    verify(service).execute(captured.capture());
    assertThat(captured.getValue().destination()).isEqualTo(Path.of("automation"));
    assertThat(captured.getValue().sutRoot()).isEqualTo(Path.of("sut"));
    assertThat(captured.getValue().transport()).isEqualTo(Transport.STREAMABLE_HTTP);
  }

  private static java.util.HashMap<String, Object> blueprintArguments() {
    return new java.util.HashMap<>(
        Map.of(
            "groupId", "com.example",
            "artifactId", "automation",
            "basePackage", "com.example.automation",
            "tafVersion", "1.3.1",
            "capabilities", List.of("WEB"),
            "runner", "TESTNG",
            "reporting", "NONE",
            "testDefinitions", "FILE_CSV",
            "publish", false));
  }
}
