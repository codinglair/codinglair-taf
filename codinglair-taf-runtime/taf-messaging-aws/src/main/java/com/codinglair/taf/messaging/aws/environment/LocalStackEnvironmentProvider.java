package com.codinglair.taf.messaging.aws.environment;

import com.codinglair.taf.messaging.aws.common.AwsConnectionProperties;
import com.codinglair.taf.messaging.aws.common.AwsEndpointMode;
import com.codinglair.taf.messaging.aws.common.AwsOwnershipMode;
import com.codinglair.taf.messaging.aws.common.AwsProperties;
import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeControllerProperties;
import com.codinglair.taf.messaging.aws.sqs.SqsControllerProperties;
import com.codinglair.taf.runtime.environment.AbstractEnvironmentProvider;
import com.codinglair.taf.runtime.environment.EnvironmentDiagnostic;
import com.codinglair.taf.runtime.environment.EnvironmentMode;
import com.codinglair.taf.runtime.environment.EnvironmentProvisioningException;
import com.codinglair.taf.runtime.environment.EnvironmentRequest;
import com.codinglair.taf.runtime.environment.EnvironmentStatus;
import com.codinglair.taf.runtime.environment.EnvironmentType;
import com.codinglair.taf.runtime.environment.PreflightCheckResult;
import com.codinglair.taf.runtime.environment.PreflightCheckType;
import com.codinglair.taf.runtime.environment.PreflightResult;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.DeleteEventBusRequest;
import software.amazon.awssdk.services.eventbridge.model.DeleteRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.DescribeEventBusRequest;
import software.amazon.awssdk.services.eventbridge.model.PutRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.RemoveTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.ResourceNotFoundException;
import software.amazon.awssdk.services.eventbridge.model.RuleState;
import software.amazon.awssdk.services.eventbridge.model.Target;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

/** Environment-provider implementation for managed and externally declared LocalStack. */
public final class LocalStackEnvironmentProvider extends AbstractEnvironmentProvider {
  public static final EnvironmentType TYPE = new EnvironmentType("aws-localstack");
  public static final String ENDPOINT_PROPERTY = "taf.aws.endpoint";
  public static final String PROFILE_PROPERTY = "taf.aws.profile";

  private final EnvironmentMode mode;
  private final AwsProperties properties;

  public LocalStackEnvironmentProvider(EnvironmentMode mode, AwsProperties properties) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.properties = Objects.requireNonNull(properties, "properties");
  }

  @Override
  public String id() {
    return "localstack-" + mode.name().toLowerCase(java.util.Locale.ROOT);
  }

  @Override
  public Set<EnvironmentMode> supportedModes() {
    return Set.of(mode);
  }

  @Override
  public PreflightResult preflight(EnvironmentRequest request) {
    var checks = new ArrayList<PreflightCheckResult>();
    AwsConnectionProperties profile;
    try {
      profile = profile(request);
      validateMode(profile);
      if (mode == EnvironmentMode.EXTERNAL) {
        requireExternalEndpoint(profile);
        try (Clients clients = clients(profile)) {
          describeExternal(profile, clients);
        }
      }
      checks.add(check(EnvironmentStatus.READY, "LocalStack configuration is valid", ""));
    } catch (RuntimeException failure) {
      checks.add(
          check(
              EnvironmentStatus.MISCONFIGURED,
              "LocalStack preflight failed",
              "Verify the endpoint, region, ownership mode, permissions, and declared resources"));
    }
    return PreflightResult.from(checks);
  }

  @Override
  protected com.codinglair.taf.runtime.environment.EnvironmentResource create(
      EnvironmentRequest request) {
    AwsConnectionProperties profile = profile(request);
    validateMode(profile);
    LocalStackContainer container = null;
    try {
      if (Thread.currentThread().isInterrupted())
        throw new IllegalStateException("LocalStack provisioning was cancelled before startup");
      if (mode == EnvironmentMode.CONTAINER) {
        container =
            new LocalStackContainer(DockerImageName.parse(properties.getLocalstackImage()))
                .withServices("sqs", "events")
                .withStartupTimeout(request.timeout());
        container.start();
        profile = withEndpoint(profile, container.getEndpoint());
      } else {
        requireExternalEndpoint(profile);
      }
      try (Clients clients = clients(profile)) {
        return provisionResource(request, profile, clients, container);
      }
    } catch (RuntimeException failure) {
      if (container != null) stop(container, failure);
      throw new EnvironmentProvisioningException(
          id(),
          request,
          "LocalStack environment provisioning failed",
          "Inspect sanitized LocalStack diagnostics and resource declarations",
          failure);
    }
  }

  private LocalStackEnvironmentResource provisionResource(
      EnvironmentRequest request,
      AwsConnectionProperties profile,
      Clients clients,
      LocalStackContainer container) {
    String owner = owner(request);
    var entries = new ArrayList<AwsOwnershipManifestEntry>();
    try {
      if (profile.getOwnershipMode() == AwsOwnershipMode.EXTERNAL) {
        describeExternal(profile, clients);
        addExternalEntries(profile, owner, entries);
      } else {
        Map<String, String> queues = provisionQueues(profile, owner, clients.sqs(), entries);
        provisionEventBuses(profile, owner, queues, clients, entries);
      }
      if (Thread.currentThread().isInterrupted())
        throw new IllegalStateException("LocalStack provisioning was cancelled");
      return new Resource(
          request.resourceName() + "-" + UUID.randomUUID(),
          mode,
          profile.getEndpointOverride(),
          request.resourceName(),
          new AwsOwnershipManifest(owner, entries),
          container,
          profile);
    } catch (RuntimeException failure) {
      cleanup(profile, new AwsOwnershipManifest(owner, entries), failure);
      throw failure;
    }
  }

  private static Map<String, String> provisionQueues(
      AwsConnectionProperties profile,
      String owner,
      SqsClient sqs,
      List<AwsOwnershipManifestEntry> entries) {
    var queues = new LinkedHashMap<String, String>();
    for (Map.Entry<String, SqsControllerProperties> declaration : profile.getSqs().entrySet()) {
      String logical = declaration.getKey();
      SqsControllerProperties settings = declaration.getValue();
      String deadLetterUrl = null;
      String deadLetterName = settings.getDeadLetterQueue();
      if (deadLetterName != null && !deadLetterName.isBlank()) {
        deadLetterUrl = createQueue(sqs, physicalName(owner, deadLetterName));
        add(entries, "sqs-dlq", logical + ".dlq", deadLetterUrl, owner, 10);
      }
      String queueUrl = createQueue(sqs, physicalName(owner, settings.getQueue()));
      queues.put(logical, queueUrl);
      add(entries, "sqs-queue", logical, queueUrl, owner, 20);
      if (deadLetterUrl != null) {
        String arn = queueArn(sqs, deadLetterUrl);
        sqs.setQueueAttributes(
            SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(
                    Map.of(
                        QueueAttributeName.REDRIVE_POLICY,
                        "{\"deadLetterTargetArn\":\"" + arn + "\",\"maxReceiveCount\":\"3\"}"))
                .build());
        add(entries, "sqs-redrive-policy", logical + ".redrive", queueUrl, owner, 30);
      }
    }
    return Map.copyOf(queues);
  }

  private static void provisionEventBuses(
      AwsConnectionProperties profile,
      String owner,
      Map<String, String> queues,
      Clients clients,
      List<AwsOwnershipManifestEntry> entries) {
    for (Map.Entry<String, EventBridgeControllerProperties> declaration :
        profile.getEventbridge().entrySet()) {
      String logical = declaration.getKey();
      EventBridgeControllerProperties settings = declaration.getValue();
      String eventBus = physicalName(owner, settings.getEventBus());
      clients.eventbridge().createEventBus(builder -> builder.name(eventBus));
      add(entries, "event-bus", logical, eventBus, owner, 40);
      if (settings.getTargetSqsController() != null) {
        String queueUrl = queues.get(settings.getTargetSqsController());
        if (queueUrl == null)
          throw new IllegalArgumentException(
              "Unknown target SQS controller: " + settings.getTargetSqsController());
        String rule = owner + "-" + logical;
        clients
            .eventbridge()
            .putRule(
                PutRuleRequest.builder()
                    .name(rule)
                    .eventBusName(eventBus)
                    .eventPattern("{\"source\":[{\"exists\":true}]}")
                    .state(RuleState.ENABLED)
                    .build());
        add(entries, "event-rule", logical + ".rule", eventBus + "/" + rule, owner, 50);
        String queueArn = queueArn(clients.sqs(), queueUrl);
        String policy =
            "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"events.amazonaws.com\"},\"Action\":\"sqs:SendMessage\",\"Resource\":\""
                + queueArn
                + "\"}]}";
        clients
            .sqs()
            .setQueueAttributes(
                SetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributes(Map.of(QueueAttributeName.POLICY, policy))
                    .build());
        add(entries, "sqs-queue-policy", logical + ".policy", queueUrl, owner, 55);
        clients
            .eventbridge()
            .putTargets(
                PutTargetsRequest.builder()
                    .eventBusName(eventBus)
                    .rule(rule)
                    .targets(
                        Target.builder().id(settings.getTargetIdentity()).arn(queueArn).build())
                    .build());
        add(
            entries,
            "event-target",
            logical + ".target",
            eventBus + "/" + rule + "/" + settings.getTargetIdentity(),
            owner,
            60);
      }
    }
  }

  private static void describeExternal(AwsConnectionProperties profile, Clients clients) {
    for (SqsControllerProperties queue : profile.getSqs().values())
      queueArn(clients.sqs(), queue.getQueue());
    for (EventBridgeControllerProperties bus : profile.getEventbridge().values())
      clients
          .eventbridge()
          .describeEventBus(DescribeEventBusRequest.builder().name(bus.getEventBus()).build());
  }

  private static void addExternalEntries(
      AwsConnectionProperties profile, String owner, List<AwsOwnershipManifestEntry> entries) {
    profile
        .getSqs()
        .forEach(
            (logical, value) ->
                addExternal(entries, "sqs-queue", logical, value.getQueue(), owner));
    profile
        .getEventbridge()
        .forEach(
            (logical, value) ->
                addExternal(entries, "event-bus", logical, value.getEventBus(), owner));
  }

  private static String createQueue(SqsClient sqs, String name) {
    return sqs.createQueue(CreateQueueRequest.builder().queueName(name).build()).queueUrl();
  }

  private static String queueArn(SqsClient sqs, String url) {
    return sqs.getQueueAttributes(
            GetQueueAttributesRequest.builder()
                .queueUrl(url)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build())
        .attributes()
        .get(QueueAttributeName.QUEUE_ARN);
  }

  private AwsConnectionProperties profile(EnvironmentRequest request) {
    AwsConnectionProperties profile = properties.getProfiles().get(request.resourceName());
    if (profile == null) throw new IllegalArgumentException("Unknown AWS profile");
    profile.validate("taf.aws.profiles." + request.resourceName());
    return profile;
  }

  private void validateMode(AwsConnectionProperties profile) {
    if (profile.getEndpointMode() != AwsEndpointMode.LOCALSTACK)
      throw new IllegalArgumentException("LocalStack provider requires LOCALSTACK endpoint mode");
    AwsOwnershipMode expected =
        mode == EnvironmentMode.CONTAINER ? AwsOwnershipMode.TEST_OWNED : AwsOwnershipMode.EXTERNAL;
    if (profile.getOwnershipMode() != expected)
      throw new IllegalArgumentException("Environment and AWS ownership modes do not agree");
  }

  private static void requireExternalEndpoint(AwsConnectionProperties profile) {
    if (profile.getEndpointOverride() == null)
      throw new IllegalArgumentException("External LocalStack requires a declared endpoint");
  }

  private static String owner(EnvironmentRequest request) {
    String value =
        request
            .properties()
            .getOrDefault("owner", request.resourceName() + "-" + UUID.randomUUID());
    if (value.isBlank())
      throw new IllegalArgumentException("AWS environment owner must not be blank");
    return value.replaceAll("[^A-Za-z0-9_-]", "-");
  }

  private static String physicalName(String owner, String declaredName) {
    String normalized = declaredName.replaceAll("[^A-Za-z0-9_-]", "-");
    String value = owner + "-" + normalized;
    return value.length() <= 80 ? value : value.substring(0, 80);
  }

  private static AwsConnectionProperties withEndpoint(
      AwsConnectionProperties source, URI endpoint) {
    var copy = new AwsConnectionProperties();
    copy.setEndpointMode(source.getEndpointMode());
    copy.setEndpointOverride(endpoint);
    copy.setRegion(source.getRegion());
    copy.setCredentialProfileReference(source.getCredentialProfileReference());
    copy.setOwnershipMode(source.getOwnershipMode());
    copy.setPolicy(source.getPolicy());
    copy.getResourceAliases().putAll(source.getResourceAliases());
    copy.getSqs().putAll(source.getSqs());
    copy.getEventbridge().putAll(source.getEventbridge());
    return copy;
  }

  private static PreflightCheckResult check(
      EnvironmentStatus status, String summary, String action) {
    return new PreflightCheckResult(
        "aws-localstack",
        PreflightCheckType.READINESS,
        Optional.empty(),
        status,
        summary,
        action,
        Map.of(),
        Instant.now());
  }

  private static Clients clients(AwsConnectionProperties profile) {
    var credentials =
        StaticCredentialsProvider.create(AwsBasicCredentials.create("localstack", "localstack"));
    URI endpoint = profile.getEndpointOverride();
    var sqs =
        SqsClient.builder()
            .region(Region.of(profile.getRegion()))
            .credentialsProvider(credentials)
            .endpointOverride(endpoint)
            .build();
    var eventbridge =
        EventBridgeClient.builder()
            .region(Region.of(profile.getRegion()))
            .credentialsProvider(credentials)
            .endpointOverride(endpoint)
            .build();
    return new Clients(sqs, eventbridge);
  }

  private static void add(
      List<AwsOwnershipManifestEntry> entries,
      String type,
      String logical,
      String physical,
      String owner,
      int order) {
    entries.add(
        new AwsOwnershipManifestEntry(
            new AwsResourceDescriptor(type, logical, physical),
            owner,
            AwsResourceCreationSource.TAF,
            AwsResourceCleanupPolicy.DELETE,
            order));
  }

  private static void addExternal(
      List<AwsOwnershipManifestEntry> entries,
      String type,
      String logical,
      String physical,
      String owner) {
    entries.add(
        new AwsOwnershipManifestEntry(
            new AwsResourceDescriptor(type, logical, physical),
            owner,
            AwsResourceCreationSource.EXTERNAL,
            AwsResourceCleanupPolicy.PRESERVE,
            0));
  }

  private static void cleanup(
      AwsConnectionProperties profile, AwsOwnershipManifest manifest, RuntimeException original) {
    if (profile.getEndpointOverride() == null) return;
    try (Clients clients = clients(profile)) {
      cleanupEntries(clients, manifest);
    } catch (RuntimeException failure) {
      original.addSuppressed(failure);
    }
  }

  private static void cleanupEntries(Clients clients, AwsOwnershipManifest manifest) {
    RuntimeException aggregate = null;
    for (AwsOwnershipManifestEntry entry : manifest.testOwnedInCleanupOrder()) {
      try {
        delete(clients, entry.resource());
      } catch (QueueDoesNotExistException | ResourceNotFoundException ignored) {
        // Idempotent cleanup treats an already absent owned resource as success.
      } catch (RuntimeException failure) {
        if (aggregate == null)
          aggregate = new IllegalStateException("One or more owned AWS resources failed cleanup");
        aggregate.addSuppressed(failure);
      }
    }
    if (aggregate != null) throw aggregate;
  }

  private static void delete(Clients clients, AwsResourceDescriptor resource) {
    String[] parts = resource.physicalId().split("/", 3);
    switch (resource.type()) {
      case "event-target" ->
          clients
              .eventbridge()
              .removeTargets(
                  RemoveTargetsRequest.builder()
                      .eventBusName(parts[0])
                      .rule(parts[1])
                      .ids(parts[2])
                      .build());
      case "event-rule" ->
          clients
              .eventbridge()
              .deleteRule(
                  DeleteRuleRequest.builder().eventBusName(parts[0]).name(parts[1]).build());
      case "event-bus" ->
          clients
              .eventbridge()
              .deleteEventBus(DeleteEventBusRequest.builder().name(resource.physicalId()).build());
      case "sqs-queue", "sqs-dlq" ->
          clients
              .sqs()
              .deleteQueue(DeleteQueueRequest.builder().queueUrl(resource.physicalId()).build());
      default -> {
        /* policies are deleted with their owned queue */
      }
    }
  }

  private static void stop(LocalStackContainer container, RuntimeException original) {
    try {
      container.stop();
    } catch (RuntimeException failure) {
      original.addSuppressed(failure);
    }
  }

  private record Clients(SqsClient sqs, EventBridgeClient eventbridge) implements AutoCloseable {
    @Override
    public void close() {
      sqs.close();
      eventbridge.close();
    }
  }

  private static final class Resource implements LocalStackEnvironmentResource {
    private final String id;
    private final EnvironmentMode mode;
    private final URI endpoint;
    private final String profileName;
    private final AwsOwnershipManifest manifest;
    private final LocalStackContainer container;
    private final AwsConnectionProperties profile;
    private final AtomicBoolean cleaned = new AtomicBoolean();

    private Resource(
        String id,
        EnvironmentMode mode,
        URI endpoint,
        String profileName,
        AwsOwnershipManifest manifest,
        LocalStackContainer container,
        AwsConnectionProperties profile) {
      this.id = id;
      this.mode = mode;
      this.endpoint = endpoint;
      this.profileName = profileName;
      this.manifest = manifest;
      this.container = container;
      this.profile = profile;
    }

    public String id() {
      return id;
    }

    public EnvironmentType type() {
      return TYPE;
    }

    public EnvironmentMode mode() {
      return mode;
    }

    public Map<String, String> properties() {
      var result = new LinkedHashMap<String, String>();
      result.put(ENDPOINT_PROPERTY, endpoint.toString());
      result.put(PROFILE_PROPERTY, profileName);
      manifest
          .entries()
          .forEach(
              entry ->
                  result.put(
                      "taf.aws.resource."
                          + entry.resource().type()
                          + "."
                          + entry.resource().logicalName(),
                      entry.resource().physicalId()));
      return Map.copyOf(result);
    }

    public AwsOwnershipManifest ownershipManifest() {
      return manifest;
    }

    @Override
    public AwsEnvironmentConfiguration effectiveConfiguration() {
      return new AwsEnvironmentConfiguration(
          profileName,
          endpoint,
          profile.getRegion(),
          manifest.entries().stream().map(AwsOwnershipManifestEntry::resource).toList());
    }

    public EnvironmentDiagnostic diagnose() {
      boolean running = container == null || container.isRunning();
      return new EnvironmentDiagnostic(
          running ? EnvironmentStatus.READY : EnvironmentStatus.UNAVAILABLE,
          running ? "LocalStack environment is ready" : "Managed LocalStack is not running",
          running ? "" : "Inspect the container runtime and LocalStack logs",
          Map.of("profile", profileName, "mode", mode.name()),
          Instant.now());
    }

    public void cleanup() {
      if (!cleaned.compareAndSet(false, true)) return;
      RuntimeException aggregate = null;
      try (Clients clients = clients(profile)) {
        cleanupEntries(clients, manifest);
      } catch (RuntimeException failure) {
        aggregate = failure;
      }
      if (container != null) {
        try {
          container.stop();
        } catch (RuntimeException failure) {
          if (aggregate == null) aggregate = failure;
          else aggregate.addSuppressed(failure);
        }
      }
      if (aggregate != null) throw aggregate;
    }
  }
}
