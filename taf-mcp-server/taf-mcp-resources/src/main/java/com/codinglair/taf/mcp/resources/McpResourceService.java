package com.codinglair.taf.mcp.resources;

import com.codinglair.taf.mcp.security.AuthorizationContext;
import com.codinglair.taf.mcp.security.AuthorizationPolicyEngine;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Single authorization boundary for capability, documentation, and evidence resource access. */
public final class McpResourceService {
  public static final String CAPABILITY_READ = "taf:capability:read";
  public static final String ARTIFACT_READ = "taf:artifact:read";

  private final RuntimeCapabilityCatalog capabilities;
  private final DocumentationCatalog documentation;
  private final EvidenceMetadataRepository evidence;
  private final AuthorizationPolicyEngine policy;
  private final List<SafeConfigurationDescriptor> configuration;
  private final List<EnvironmentReadinessDescriptor> environments;
  private final List<CapabilityInstanceDescriptor> instances;

  public McpResourceService(
      RuntimeCapabilityCatalog capabilities,
      DocumentationCatalog documentation,
      EvidenceMetadataRepository evidence,
      AuthorizationPolicyEngine policy,
      Collection<SafeConfigurationDescriptor> configuration,
      Collection<EnvironmentReadinessDescriptor> environments) {
    this(capabilities, documentation, evidence, policy, configuration, environments, List.of());
  }

  public McpResourceService(
      RuntimeCapabilityCatalog capabilities,
      DocumentationCatalog documentation,
      EvidenceMetadataRepository evidence,
      AuthorizationPolicyEngine policy,
      Collection<SafeConfigurationDescriptor> configuration,
      Collection<EnvironmentReadinessDescriptor> environments,
      Collection<CapabilityInstanceDescriptor> instances) {
    this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
    this.documentation = Objects.requireNonNull(documentation, "documentation");
    this.evidence = Objects.requireNonNull(evidence, "evidence");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.configuration =
        configuration.stream()
            .sorted(Comparator.comparing(SafeConfigurationDescriptor::key))
            .toList();
    this.environments =
        environments.stream()
            .sorted(Comparator.comparing(EnvironmentReadinessDescriptor::environment))
            .toList();
    this.instances =
        instances.stream()
            .sorted(
                Comparator.comparing(CapabilityInstanceDescriptor::capabilityId)
                    .thenComparing(CapabilityInstanceDescriptor::instance))
            .toList();
  }

  public ResourcePage<CapabilityInstanceDescriptor> discoverCapabilityInstances(
      ResourceRequestContext caller, ResourceQuery query) {
    authorize(caller, "discover", CAPABILITY_READ);
    return DeterministicPaginator.page(
        instances,
        query,
        item ->
            item.capabilityId()
                + " "
                + item.instance()
                + " "
                + item.resourceAlias()
                + " "
                + item.readiness());
  }

  public ResourcePage<RuntimeCapabilityDescriptor> discoverCapabilities(
      ResourceRequestContext caller, ResourceQuery query) {
    authorize(caller, "discover", CAPABILITY_READ);
    return capabilities.discover(query);
  }

  public ResourcePage<DocumentationResource> discoverDocumentation(
      ResourceRequestContext caller, ResourceQuery query) {
    authorize(caller, "retrieve", CAPABILITY_READ);
    return documentation.discover(query);
  }

  public ResourcePage<SafeConfigurationDescriptor> discoverConfiguration(
      ResourceRequestContext caller, ResourceQuery query) {
    authorize(caller, "discover", CAPABILITY_READ);
    return DeterministicPaginator.page(
        configuration, query, item -> item.key() + " " + item.source());
  }

  public ResourcePage<EnvironmentReadinessDescriptor> discoverEnvironments(
      ResourceRequestContext caller, ResourceQuery query) {
    authorize(caller, "discover", CAPABILITY_READ);
    return DeterministicPaginator.page(
        environments, query, item -> item.environment() + " " + item.status());
  }

  public DocumentationResource readDocumentation(
      ResourceRequestContext caller, String id, String version) {
    authorize(caller, "retrieve", CAPABILITY_READ);
    return documentation.find(id, version).orElseThrow(ResourceAccessDeniedException::new);
  }

  public ResourcePage<EvidenceMetadata> discoverEvidence(
      ResourceRequestContext caller, String jobId, ResourceQuery query) {
    authorize(caller, "retrieve", ARTIFACT_READ);
    if (jobId == null || jobId.isBlank()) {
      throw new IllegalArgumentException("jobId is required");
    }
    var ordered =
        evidence.findByJobId(jobId).stream()
            .filter(item -> item.access().permits(caller) && item.metadata().jobId().equals(jobId))
            .map(StoredEvidenceMetadata::metadata)
            .sorted(Comparator.comparing(EvidenceMetadata::id))
            .toList();
    return DeterministicPaginator.page(
        ordered, query, item -> item.evidenceId() + " " + item.mediaType());
  }

  private void authorize(ResourceRequestContext caller, String action, String permission) {
    Objects.requireNonNull(caller, "caller");
    var context =
        new AuthorizationContext(
            caller.identity(), caller.project(), caller.environment(), action, permission);
    if (!policy.decide(context).allowed()) {
      throw new ResourceAccessDeniedException();
    }
  }
}
