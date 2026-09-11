package com.codinglair.taf.mcp.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.core.Capability;
import com.codinglair.taf.mcp.security.AuthorizationPolicyEngine;
import com.codinglair.taf.mcp.security.CallerIdentity;
import com.codinglair.taf.mcp.security.IdentityKind;
import com.codinglair.taf.mcp.security.PolicyRule;
import com.codinglair.taf.runtime.environment.EnvironmentStatus;
import com.codinglair.taf.runtime.environment.PreflightCheckResult;
import com.codinglair.taf.runtime.environment.PreflightCheckType;
import com.codinglair.taf.runtime.environment.PreflightResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MCP controlled resource service")
class McpResourceServiceTest {
  private static final ResourceRequestContext CALLER =
      new ResourceRequestContext(
          new CallerIdentity("user", Set.of("tester"), "agent", IdentityKind.AGENT),
          "project",
          "local");

  @Nested
  @DisplayName("Capability discovery")
  class CapabilityDiscovery {
    @Test
    @DisplayName(
        "reports installed and absent capabilities with their authoritative Runtime versions")
    void reportsInstalledAndAbsentCapabilities() {
      var catalog = largeCatalog(2, Map.of("capability-001", "1.7.3"), "1.7.0");
      var service = service(catalog, allowedPolicy(), _ -> List.of());

      var page = service.discoverCapabilities(CALLER, new ResourceQuery("", 10, null));

      assertThat(page.items())
          .extracting(RuntimeCapabilityDescriptor::id)
          .containsExactly("capability-000", "capability-001");
      assertThat(page.items().getFirst())
          .extracting(
              RuntimeCapabilityDescriptor::runtimeVersion, RuntimeCapabilityDescriptor::status)
          .containsExactly("1.7.0", RuntimeCapabilityDescriptor.InstallationStatus.ABSENT);
      assertThat(page.items().getLast())
          .extracting(
              RuntimeCapabilityDescriptor::runtimeVersion, RuntimeCapabilityDescriptor::status)
          .containsExactly("1.7.3", RuntimeCapabilityDescriptor.InstallationStatus.INSTALLED);
    }

    @Test
    @DisplayName("reports stable EventBridge and SQS operations limitations and configuration")
    void reportsAwsCapabilityDetails() {
      var capabilities =
          List.of(
              new Capability(
                  "aws.eventbridge", "EventBridge", Capability.CapabilityType.CONTROLLER),
              new Capability("aws.sqs", "SQS", Capability.CapabilityType.CONTROLLER));
      var catalog =
          new RuntimeCapabilityCatalog(
              capabilities, Map.of("aws.eventbridge", "1.1.0", "aws.sqs", "1.1.0"), "1.1.0");

      assertThat(catalog.descriptors())
          .allSatisfy(
              descriptor -> {
                assertThat(descriptor.operations()).isNotEmpty();
                assertThat(descriptor.limitations()).isNotEmpty();
                assertThat(descriptor.requiredConfiguration()).isNotEmpty();
              });
    }

    @Test
    @DisplayName("paginates a large catalog deterministically without duplicates")
    void paginatesLargeCatalogDeterministically() {
      var service = service(largeCatalog(257, Map.of(), "2.1.0"), allowedPolicy(), _ -> List.of());

      var firstPass = readAllCapabilities(service, 23);
      var secondPass = readAllCapabilities(service, 23);

      assertThat(firstPass).hasSize(257).doesNotHaveDuplicates().isSorted();
      assertThat(secondPass).containsExactlyElementsOf(firstPass);
    }

    @Test
    @DisplayName("binds continuation cursors to the original filter")
    void bindsCursorToFilter() {
      var service = service(largeCatalog(10, Map.of(), "1.0.0"), allowedPolicy(), _ -> List.of());
      var first = service.discoverCapabilities(CALLER, new ResourceQuery("capability", 2, null));

      assertThrows(
          IllegalArgumentException.class,
          () ->
              service.discoverCapabilities(
                  CALLER, new ResourceQuery("different", 2, first.nextCursor().orElseThrow())));
    }

    @Test
    @DisplayName("serves concurrent catalog readers without shared pagination state")
    void servesConcurrentReaders() throws Exception {
      var service = service(largeCatalog(101, Map.of(), "1.0.0"), allowedPolicy(), _ -> List.of());
      List<Callable<List<String>>> readers =
          java.util.stream.IntStream.range(0, 32)
              .mapToObj(_ -> (Callable<List<String>>) () -> readAllCapabilities(service, 7))
              .toList();

      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var results =
            executor.invokeAll(readers).stream().map(future -> get(future)).distinct().toList();

        assertThat(results).singleElement().satisfies(items -> assertThat(items).hasSize(101));
      }
    }
  }

  @Nested
  @DisplayName("Access control")
  class AccessControl {
    @Test
    @DisplayName("denies evidence before consulting its repository")
    void deniesBeforeRepositoryLookup() {
      var consulted = new AtomicBoolean();
      EvidenceMetadataRepository repository =
          _ -> {
            consulted.set(true);
            return List.of();
          };
      var service = service(largeCatalog(1, Map.of(), "1.0.0"), deniedPolicy(), repository);

      var failure =
          assertThrows(
              ResourceAccessDeniedException.class,
              () -> service.discoverEvidence(CALLER, "job-1", new ResourceQuery("", 10, null)));

      assertThat(failure).hasMessage("resource is unavailable");
      assertThat(consulted).isFalse();
    }

    @Test
    @DisplayName("filters evidence only inside an authorized job scope")
    void filtersEvidenceInsideAuthorizedJob() {
      var created = Instant.parse("2026-08-22T00:00:00Z");
      EvidenceMetadataRepository repository =
          _ ->
              List.of(
                  stored("user", evidence("job-1", "b", "application/json", created)),
                  stored("user", evidence("other-job", "hidden", "text/plain", created)),
                  stored("other-user", evidence("job-1", "other-owner", "text/plain", created)),
                  stored("user", evidence("job-1", "a", "text/plain", created)));
      var service = service(largeCatalog(1, Map.of(), "1.0.0"), allowedPolicy(), repository);

      var page =
          service.discoverEvidence(CALLER, "job-1", new ResourceQuery("text/plain", 10, null));

      assertThat(page.items()).extracting(EvidenceMetadata::id).containsExactly("job-1/a");
    }
  }

  @Nested
  @DisplayName("Documentation and safe metadata")
  class DocumentationAndSafeMetadata {
    @Test
    @DisplayName("discovers multiple safe named AWS instances without sensitive runtime material")
    void discoversNamedAwsInstances() throws Exception {
      var instances =
          List.of(
              new CapabilityInstanceDescriptor(
                  "aws.sqs",
                  "orders-b",
                  "queue/orders-b",
                  "EXTERNAL",
                  "CONTROLLED_CONSUMER",
                  EnvironmentStatus.DEGRADED,
                  List.of("permission-limited")),
              new CapabilityInstanceDescriptor(
                  "aws.sqs",
                  "orders-a",
                  "queue/orders-a",
                  "TEST_OWNED",
                  "DEDICATED_RESOURCE",
                  EnvironmentStatus.READY,
                  List.of()));
      var service =
          new McpResourceService(
              largeCatalog(1, Map.of(), "1.0.0"),
              McpContractDocumentation.load(getClass().getClassLoader()),
              _ -> List.of(),
              allowedPolicy(),
              List.of(),
              List.of(),
              instances);

      var page =
          service.discoverCapabilityInstances(CALLER, new ResourceQuery("aws.sqs", 10, null));
      var json = new ObjectMapper().writeValueAsString(page.items());

      assertThat(page.items())
          .extracting(CapabilityInstanceDescriptor::instance)
          .containsExactly("orders-a", "orders-b");
      assertThat(json).doesNotContain("receiptHandle", "credential", "https://");
    }

    @Test
    @DisplayName("rejects secret-bearing endpoint and diagnostic material at construction")
    void rejectsUnsafeInstanceMaterial() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new CapabilityInstanceDescriptor(
                  "aws.sqs",
                  "orders",
                  "https://user:canary-secret@localhost/queue",
                  "EXTERNAL",
                  "CONTROLLED_CONSUMER",
                  EnvironmentStatus.READY,
                  List.of("token=canary-secret")));
    }

    @Test
    @DisplayName("loads versioned documentation from the authoritative MCP contract artifact")
    void loadsVersionedContractDocumentation() {
      var catalog = McpContractDocumentation.load(getClass().getClassLoader());

      var document = catalog.find("mcp-capability-catalog", "1.0.0").orElseThrow();

      assertThat(document.uri()).isEqualTo("taf://documentation/mcp-capability-catalog/1.0.0");
      assertThat(document.sizeBytes())
          .isEqualTo(document.content().getBytes(StandardCharsets.UTF_8).length)
          .isLessThanOrEqualTo(DocumentationResource.MAXIMUM_CONTENT_BYTES);
    }

    @Test
    @DisplayName("configuration discovery exposes metadata but has no value field")
    void exposesNoConfigurationValues() throws Exception {
      var configuration =
          new SafeConfigurationDescriptor(
              "taf.consumer.capabilities.web.enabled", true, "environment");
      var service =
          service(
              largeCatalog(1, Map.of(), "1.0.0"),
              allowedPolicy(),
              _ -> List.of(),
              List.of(configuration),
              List.of());

      var page = service.discoverConfiguration(CALLER, new ResourceQuery("web", 10, null));
      var json = new ObjectMapper().writeValueAsString(page.items().getFirst());

      assertThat(json).contains("configured", "source").doesNotContain("value", "password-canary");
    }

    @Test
    @DisplayName("environment discovery omits messages diagnostics and secret canaries")
    void omitsEnvironmentDiagnostics() throws Exception {
      var check =
          new PreflightCheckResult(
              "database",
              PreflightCheckType.DATABASE_SCHEMA,
              Optional.empty(),
              EnvironmentStatus.MISCONFIGURED,
              "password-canary",
              "token-canary",
              Map.of("secret", "credential-canary"),
              Instant.parse("2026-08-22T00:00:00Z"));
      var environment =
          EnvironmentReadinessDescriptor.from("local", PreflightResult.from(List.of(check)));
      var service =
          service(
              largeCatalog(1, Map.of(), "1.0.0"),
              allowedPolicy(),
              _ -> List.of(),
              List.of(),
              List.of(environment));

      var page = service.discoverEnvironments(CALLER, new ResourceQuery("", 10, null));
      var json = new ObjectMapper().writeValueAsString(page.items().getFirst());

      assertThat(json)
          .contains("MISCONFIGURED", "database")
          .doesNotContain("password-canary", "token-canary", "credential-canary", "diagnostics");
    }
  }

  private static List<String> readAllCapabilities(McpResourceService service, int pageSize) {
    var result = new ArrayList<String>();
    String cursor = null;
    do {
      var page = service.discoverCapabilities(CALLER, new ResourceQuery("", pageSize, cursor));
      result.addAll(page.items().stream().map(RuntimeCapabilityDescriptor::id).toList());
      cursor = page.nextCursor().orElse(null);
    } while (cursor != null);
    return result;
  }

  private static RuntimeCapabilityCatalog largeCatalog(
      int size, Map<String, String> installed, String baselineVersion) {
    var capabilities =
        java.util.stream.IntStream.range(0, size)
            .mapToObj(
                index ->
                    new Capability(
                        "capability-%03d".formatted(index),
                        "Runtime capability %03d".formatted(index),
                        Capability.CapabilityType.CONTROLLER))
            .toList();
    return new RuntimeCapabilityCatalog(capabilities, installed, baselineVersion);
  }

  private static List<String> get(java.util.concurrent.Future<List<String>> future) {
    try {
      return future.get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("concurrent catalog read interrupted", exception);
    } catch (java.util.concurrent.ExecutionException exception) {
      throw new IllegalStateException("concurrent catalog read failed", exception.getCause());
    }
  }

  private static EvidenceMetadata evidence(
      String jobId, String evidenceId, String mediaType, Instant createdAt) {
    return new EvidenceMetadata(
        jobId,
        evidenceId,
        "taf://evidence/" + jobId + "/" + evidenceId,
        mediaType,
        12,
        "0123456789abcdef",
        createdAt);
  }

  private static StoredEvidenceMetadata stored(String owner, EvidenceMetadata metadata) {
    return new StoredEvidenceMetadata(new EvidenceAccessScope(owner, "project", "local"), metadata);
  }

  private static McpResourceService service(
      RuntimeCapabilityCatalog catalog,
      AuthorizationPolicyEngine policy,
      EvidenceMetadataRepository repository) {
    return service(catalog, policy, repository, List.of(), List.of());
  }

  private static McpResourceService service(
      RuntimeCapabilityCatalog catalog,
      AuthorizationPolicyEngine policy,
      EvidenceMetadataRepository repository,
      List<SafeConfigurationDescriptor> configuration,
      List<EnvironmentReadinessDescriptor> environments) {
    return new McpResourceService(
        catalog,
        McpContractDocumentation.load(McpResourceServiceTest.class.getClassLoader()),
        repository,
        policy,
        configuration,
        environments);
  }

  private static AuthorizationPolicyEngine allowedPolicy() {
    return new AuthorizationPolicyEngine(
        List.of(
            new PolicyRule(
                "resource-read",
                PolicyRule.Effect.ALLOW,
                Set.of("*"),
                Set.of("*"),
                Set.of("*"),
                Set.of("*"),
                Set.of("discover", "retrieve"),
                Set.of("*"),
                Set.of(McpResourceService.CAPABILITY_READ, McpResourceService.ARTIFACT_READ),
                false)));
  }

  private static AuthorizationPolicyEngine deniedPolicy() {
    return new AuthorizationPolicyEngine(List.of());
  }
}
