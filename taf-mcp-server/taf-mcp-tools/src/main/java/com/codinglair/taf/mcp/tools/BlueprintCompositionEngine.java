package com.codinglair.taf.mcp.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Pure deterministic blueprint planning followed by an atomic create-only directory write. */
public final class BlueprintCompositionEngine {
  private static final Pattern GROUP = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+");
  private static final Pattern ARTIFACT = Pattern.compile("[a-z][a-z0-9-]*");
  private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]*");
  private static final Pattern TOKEN = Pattern.compile("__[A-Z][A-Z0-9_]*__");
  private static final Comparator<Diagnostic> DIAGNOSTIC_ORDER =
      Comparator.comparing(Diagnostic::phase)
          .thenComparing(d -> value(d.target()))
          .thenComparing(d -> value(d.pointer()))
          .thenComparing(Diagnostic::code)
          .thenComparing(d -> value(d.contributionId()));

  /** Builds a complete immutable plan without mutating the destination. */
  public CompositionPlan plan(
      Request request,
      String blueprintVersion,
      List<Contribution> available,
      StarterManifest manifest,
      Path destination) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(available, "available");
    Objects.requireNonNull(manifest, "manifest");
    Objects.requireNonNull(destination, "destination");
    var diagnostics = new ArrayList<Diagnostic>();
    var normalized = normalize(request, blueprintVersion, diagnostics);
    if (normalized == null) return failed(null, blueprintVersion, diagnostics);

    var selected = select(normalized, available, diagnostics);
    validateContributions(selected, blueprintVersion, diagnostics);
    var dependencies =
        resolveDependencies(selected, manifest, normalized.tafVersion(), diagnostics);
    var writes = render(selected, normalized, destination, diagnostics);
    validateConfiguration(selected, diagnostics);
    var configuration =
        selected.stream()
            .flatMap(
                contribution ->
                    contribution.configurationClaims().stream()
                        .map(claim -> new PlannedConfiguration(contribution.id(), claim)))
            .toList();
    diagnostics.sort(DIAGNOSTIC_ORDER);
    if (!diagnostics.isEmpty()) return failed(normalized, blueprintVersion, diagnostics);
    return new CompositionPlan(
        normalized,
        blueprintVersion,
        selected.stream().map(Contribution::id).toList(),
        dependencies,
        configuration,
        writes,
        List.of());
  }

  /** Writes a valid plan by atomically publishing a fully populated sibling staging directory. */
  public WriteResult write(CompositionPlan plan, Path destination) {
    Objects.requireNonNull(plan, "plan");
    Objects.requireNonNull(destination, "destination");
    if (!plan.valid())
      throw new IllegalArgumentException("only a valid composition plan can be written");
    var absolute = destination.toAbsolutePath().normalize();
    var parent = absolute.getParent();
    if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
      return new WriteResult(WriteStatus.FAILED, "destination parent is unavailable");
    }
    if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
      return new WriteResult(WriteStatus.CONFLICT, "destination already exists");
    }
    Path staging = null;
    try {
      staging = Files.createTempDirectory(parent, ".taf-compose-");
      for (var write : plan.writes()) {
        var target = staging.resolve(write.path()).normalize();
        if (!target.startsWith(staging)) throw new IOException("planned path escaped staging");
        Files.createDirectories(target.getParent());
        Files.write(target, write.content(), StandardOpenOption.CREATE_NEW);
      }
      Files.move(staging, absolute, StandardCopyOption.ATOMIC_MOVE);
      return new WriteResult(WriteStatus.WRITTEN, "composition written");
    } catch (FileAlreadyExistsException _) {
      deleteTree(staging);
      return new WriteResult(WriteStatus.CONFLICT, "destination already exists");
    } catch (AtomicMoveNotSupportedException _) {
      deleteTree(staging);
      return new WriteResult(WriteStatus.FAILED, "atomic destination publish is unsupported");
    } catch (IOException _) {
      deleteTree(staging);
      if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
        return new WriteResult(WriteStatus.CONFLICT, "destination already exists");
      }
      return new WriteResult(WriteStatus.FAILED, "composition write failed safely");
    }
  }

  private static NormalizedRequest normalize(
      Request request, String blueprintVersion, List<Diagnostic> diagnostics) {
    String group = trim(request.groupId());
    String artifact = trim(request.artifactId());
    String basePackage = trim(request.basePackage());
    String tafVersion = trim(request.tafVersion());
    if (!"1.0".equals(blueprintVersion)
        || !matches(GROUP, group)
        || !matches(ARTIFACT, artifact)
        || !matches(GROUP, basePackage)
        || !matches(VERSION, tafVersion)) {
      diagnostics.add(
          error(
              "SCF_INVALID_REQUEST",
              Phase.REQUEST,
              null,
              null,
              null,
              "project coordinates or blueprint version are invalid",
              "Correct the named value and resubmit."));
      return null;
    }
    var capabilities = parseCapabilities(request.capabilities(), diagnostics);
    var provider = token(request.messagingProvider());
    if (capabilities.contains(Capability.MESSAGING) && provider == null) {
      diagnostics.add(
          error(
              "SCF_INCOMPLETE_SELECTION",
              Phase.SELECTION,
              null,
              null,
              null,
              "messaging requires exactly one provider",
              "Select AWS, JMS, KAFKA, or RABBITMQ."));
    } else if (!capabilities.contains(Capability.MESSAGING) && provider != null) {
      diagnostics.add(
          error(
              "SCF_UNSUPPORTED_SELECTION",
              Phase.SELECTION,
              null,
              null,
              null,
              "a messaging provider requires MESSAGING",
              "Remove the provider or select MESSAGING."));
    }
    MessagingProvider messaging = enumValue(MessagingProvider.class, provider);
    if (provider != null && messaging == null) unsupported(diagnostics, "messaging provider");
    var platformToken = token(request.mobilePlatform());
    var automationToken = token(request.mobileAutomationName());
    MobilePlatform platform = enumValue(MobilePlatform.class, platformToken);
    if (capabilities.contains(Capability.MOBILE)) {
      if (platformToken == null) platform = MobilePlatform.ANDROID;
      if (platform == MobilePlatform.IOS) {
        diagnostics.add(
            error(
                "SCF_UNSUPPORTED_IOS",
                Phase.SELECTION,
                null,
                null,
                null,
                "iOS is not implemented in blueprint 1.0",
                "Select Android/UiAutomator2."));
      } else if (platform == null) unsupported(diagnostics, "mobile platform");
      if (automationToken != null && !"UIAUTOMATOR2".equals(automationToken)) {
        unsupported(diagnostics, "mobile automation name");
      }
    } else if (platformToken != null || automationToken != null) {
      unsupported(diagnostics, "mobile options without MOBILE");
    }
    Runner runner = defaulted(Runner.class, request.runner(), Runner.TESTNG, diagnostics);
    Reporting reporting =
        defaulted(Reporting.class, request.reporting(), Reporting.ALLURE, diagnostics);
    TestDefinitions definitions =
        defaulted(
            TestDefinitions.class,
            request.testDefinitions(),
            TestDefinitions.FILE_CSV,
            diagnostics);
    if (!diagnostics.isEmpty()) return null;
    return new NormalizedRequest(
        "1.0",
        blueprintVersion,
        group,
        artifact,
        basePackage,
        tafVersion,
        List.copyOf(capabilities),
        Optional.ofNullable(messaging),
        capabilities.contains(Capability.MOBILE)
            ? Optional.of(MobilePlatform.ANDROID)
            : Optional.empty(),
        capabilities.contains(Capability.MOBILE) ? Optional.of("UIAUTOMATOR2") : Optional.empty(),
        runner,
        reporting,
        definitions);
  }

  private static List<Contribution> select(
      NormalizedRequest request, List<Contribution> available, List<Diagnostic> diagnostics) {
    var selected =
        available.stream()
            .filter(c -> c.selector().matches(request))
            .sorted(Comparator.comparingInt(Contribution::order).thenComparing(Contribution::id))
            .toList();
    if (selected.stream().filter(c -> c.kind() == Kind.COMMON).count() != 1) {
      diagnostics.add(
          error(
              "SCF_UNSUPPORTED_SELECTION",
              Phase.SELECTION,
              null,
              null,
              null,
              "exactly one common contribution must match",
              "Repair the approved blueprint catalog."));
    }
    for (var capability : request.capabilities()) {
      if (selected.stream()
              .filter(c -> c.kind() == Kind.CAPABILITY && c.selector().capability() == capability)
              .count()
          != 1) {
        diagnostics.add(
            error(
                "SCF_UNSUPPORTED_SELECTION",
                Phase.SELECTION,
                null,
                null,
                null,
                "exactly one contribution must match " + capability,
                "Choose a supported capability combination."));
      }
    }
    return selected;
  }

  private static void validateContributions(
      List<Contribution> selected, String version, List<Diagnostic> diagnostics) {
    var ids = new TreeSet<String>();
    var orderKeys = new TreeSet<String>();
    for (var contribution : selected) {
      int minimum = contribution.kind().minimum;
      if (!version.equals(contribution.blueprintVersion())
          || !ids.add(contribution.id())
          || !orderKeys.add(contribution.order() + "\u0000" + contribution.id())
          || contribution.order() < minimum
          || contribution.order() > minimum + 99) {
        diagnostics.add(
            error(
                "SCF_UNSUPPORTED_SELECTION",
                Phase.CONTRIBUTION,
                contribution.id(),
                null,
                null,
                "contribution identity, version, or order is invalid",
                "Repair the approved blueprint contribution."));
      }
    }
  }

  private static List<Dependency> resolveDependencies(
      List<Contribution> selected,
      StarterManifest manifest,
      String version,
      List<Diagnostic> diagnostics) {
    var resolved = new LinkedHashMap<String, Dependency>();
    for (var contribution : selected) {
      for (var reference : contribution.manifestIds()) {
        var artifact = manifest.starters().get(reference);
        if (artifact == null) {
          diagnostics.add(
              error(
                  "SCF_MANIFEST_REFERENCE",
                  Phase.MANIFEST,
                  contribution.id(),
                  null,
                  null,
                  "starter manifest ID is unavailable: " + reference,
                  "Repair the blueprint/manifest version pair."));
        } else {
          resolved.putIfAbsent(reference, new Dependency(manifest.groupId(), artifact, version));
        }
      }
    }
    return List.copyOf(resolved.values());
  }

  private static List<PlannedWrite> render(
      List<Contribution> selected,
      NormalizedRequest request,
      Path destination,
      List<Diagnostic> diagnostics) {
    var writes = new LinkedHashMap<String, PlannedWrite>();
    var folded = new TreeSet<String>();
    var tokens =
        Map.of(
            "__GROUP_ID__", request.groupId(),
            "__ARTIFACT_ID__", request.artifactId(),
            "__BASE_PACKAGE__", request.basePackage(),
            "__PACKAGE_PATH__", request.basePackage().replace('.', '/'),
            "__TAF_VERSION__", request.tafVersion());
    for (var contribution : selected) {
      for (var asset : contribution.assets()) {
        String path = replace(asset.path(), tokens).replace('\\', '/');
        String content = replace(asset.content(), tokens).replace("\r\n", "\n").replace('\r', '\n');
        if (!safePath(path) || TOKEN.matcher(path).find()) {
          diagnostics.add(
              error(
                  "SCF_PATH_INVALID",
                  Phase.PATH,
                  contribution.id(),
                  path,
                  null,
                  "target path is unsafe",
                  "Use a normalized relative path below the destination."));
          continue;
        }
        if (TOKEN.matcher(content).find()) {
          diagnostics.add(
              error(
                  "SCF_UNRESOLVED_TOKEN",
                  Phase.RENDER,
                  contribution.id(),
                  path,
                  null,
                  "rendered content contains an unresolved token",
                  "Provide the value or repair the contribution."));
          continue;
        }
        String fold = path.toLowerCase(Locale.ROOT);
        if (writes.containsKey(path) || !folded.add(fold)) {
          diagnostics.add(
              error(
                  "SCF_PATH_COLLISION",
                  Phase.PATH,
                  contribution.id(),
                  path,
                  null,
                  "multiple contributions own the same target",
                  "Assign the target to one contribution."));
          continue;
        }
        Path target = destination.toAbsolutePath().normalize().resolve(path).normalize();
        if (!target.startsWith(destination.toAbsolutePath().normalize())
            || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
          diagnostics.add(
              error(
                  "SCF_PATH_COLLISION",
                  Phase.PATH,
                  contribution.id(),
                  path,
                  null,
                  "target already exists or escapes the destination",
                  "Choose an empty destination."));
          continue;
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        writes.put(
            path,
            new PlannedWrite(Path.of(path), bytes, sha256(bytes), asset.dependencyMetadata()));
      }
    }
    return List.copyOf(writes.values());
  }

  private static void validateConfiguration(
      List<Contribution> selected, List<Diagnostic> diagnostics) {
    var claims = new ArrayList<ClaimOwner>();
    for (var contribution : selected) {
      for (var claim : contribution.configurationClaims()) {
        for (var existing : claims) {
          if (existing.claim.document().equals(claim.document())
              && overlaps(existing.claim.pointer(), claim.pointer())
              && !(existing.claim.rule() == MergeRule.REQUIRE_EQUAL
                  && claim.rule() == MergeRule.REQUIRE_EQUAL
                  && Objects.equals(existing.claim.canonicalValue(), claim.canonicalValue()))) {
            diagnostics.add(
                error(
                    "SCF_CONFIG_COLLISION",
                    Phase.CONFIGURATION,
                    contribution.id(),
                    claim.document(),
                    claim.pointer(),
                    "configuration ownership overlaps incompatibly",
                    "Assign one owner or use equal REQUIRE_EQUAL values."));
          }
        }
        claims.add(new ClaimOwner(contribution.id(), claim));
      }
    }
  }

  private static boolean overlaps(String left, String right) {
    return left.equals(right) || left.startsWith(right + "/") || right.startsWith(left + "/");
  }

  private static boolean safePath(String value) {
    if (value == null || value.isBlank() || value.startsWith("/") || value.contains("//"))
      return false;
    Path path;
    try {
      path = Path.of(value);
    } catch (RuntimeException _) {
      return false;
    }
    if (path.isAbsolute() || value.contains("\\") || path.getNameCount() == 0) return false;
    for (var segment : value.split("/", -1))
      if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) return false;
    return path.normalize().toString().replace('\\', '/').equals(value);
  }

  private static <E extends Enum<E>> E defaulted(
      Class<E> type, String input, E fallback, List<Diagnostic> diagnostics) {
    String value = token(input);
    if (value == null) return fallback;
    E parsed = enumValue(type, value);
    if (parsed == null) unsupported(diagnostics, type.getSimpleName());
    return parsed;
  }

  private static List<Capability> parseCapabilities(
      List<String> values, List<Diagnostic> diagnostics) {
    var result = EnumSet.noneOf(Capability.class);
    if (values == null || values.isEmpty()) {
      diagnostics.add(
          error(
              "SCF_INVALID_REQUEST",
              Phase.REQUEST,
              null,
              null,
              null,
              "at least one capability is required",
              "Select a supported capability."));
      return List.of();
    }
    for (var value : values) {
      var capability = enumValue(Capability.class, token(value));
      if (capability == null) unsupported(diagnostics, "capability");
      else if (!result.add(capability)) {
        diagnostics.add(
            error(
                "SCF_INVALID_REQUEST",
                Phase.REQUEST,
                null,
                null,
                null,
                "capability selections must be unique",
                "Remove the duplicate capability."));
      }
    }
    return result.stream().toList();
  }

  private static void unsupported(List<Diagnostic> diagnostics, String subject) {
    diagnostics.add(
        error(
            "SCF_UNSUPPORTED_SELECTION",
            Phase.SELECTION,
            null,
            null,
            null,
            "unsupported " + subject,
            "Choose a value in the release capability matrix."));
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
    if (value == null) return null;
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException _) {
      return null;
    }
  }

  private static String replace(String value, Map<String, String> replacements) {
    String result = value;
    for (var entry : replacements.entrySet())
      result = result.replace(entry.getKey(), entry.getValue());
    return result;
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }

  private static String token(String value) {
    String trimmed = trim(value);
    return trimmed == null || trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
  }

  private static boolean matches(Pattern pattern, String value) {
    return value != null && value.length() <= 200 && pattern.matcher(value).matches();
  }

  private static CompositionPlan failed(
      NormalizedRequest request, String version, List<Diagnostic> diagnostics) {
    diagnostics.sort(DIAGNOSTIC_ORDER);
    return new CompositionPlan(
        request, version, List.of(), List.of(), List.of(), List.of(), diagnostics);
  }

  private static Diagnostic error(
      String code,
      Phase phase,
      String contribution,
      String target,
      String pointer,
      String message,
      String action) {
    return new Diagnostic(code, phase, contribution, target, pointer, message, action);
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static void deleteTree(Path root) {
    if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
    try (var paths = Files.walk(root)) {
      for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
    } catch (IOException _) {
      // Staging cleanup is best effort; the destination is never partially published.
    }
  }

  public enum Capability {
    API,
    DATABASE,
    MESSAGING,
    MOBILE,
    WEB
  }

  public enum MessagingProvider {
    AWS,
    JMS,
    KAFKA,
    RABBITMQ
  }

  public enum MobilePlatform {
    ANDROID,
    IOS
  }

  public enum Runner {
    TESTNG,
    CUCUMBER_TESTNG
  }

  public enum Reporting {
    ALLURE,
    NONE
  }

  public enum TestDefinitions {
    FILE_CSV,
    MONGODB
  }

  public enum MergeRule {
    APPEND_UNIQUE_BY_ID,
    DEEP_MERGE_NO_OVERWRITE,
    REQUIRE_EQUAL,
    SET_IF_ABSENT
  }

  public enum WriteStatus {
    WRITTEN,
    CONFLICT,
    FAILED
  }

  public enum Phase {
    REQUEST,
    SELECTION,
    CONTRIBUTION,
    MANIFEST,
    PATH,
    CONFIGURATION,
    RENDER
  }

  public enum Kind {
    COMMON(100),
    CAPABILITY(200),
    PROVIDER(300),
    RUNNER(400),
    REPORTING(500);
    private final int minimum;

    Kind(int minimum) {
      this.minimum = minimum;
    }
  }

  public record Request(
      String groupId,
      String artifactId,
      String basePackage,
      String tafVersion,
      List<String> capabilities,
      String messagingProvider,
      String mobilePlatform,
      String mobileAutomationName,
      String runner,
      String reporting,
      String testDefinitions) {
    public Request {
      capabilities = capabilities == null ? null : List.copyOf(capabilities);
    }
  }

  public record NormalizedRequest(
      String schemaVersion,
      String blueprintVersion,
      String groupId,
      String artifactId,
      String basePackage,
      String tafVersion,
      List<Capability> capabilities,
      Optional<MessagingProvider> messagingProvider,
      Optional<MobilePlatform> mobilePlatform,
      Optional<String> mobileAutomationName,
      Runner runner,
      Reporting reporting,
      TestDefinitions testDefinitions) {
    public NormalizedRequest {
      capabilities = List.copyOf(capabilities);
      messagingProvider = Objects.requireNonNull(messagingProvider);
      mobilePlatform = Objects.requireNonNull(mobilePlatform);
      mobileAutomationName = Objects.requireNonNull(mobileAutomationName);
    }
  }

  public record Selector(
      Kind kind,
      Capability capability,
      MessagingProvider provider,
      Runner runner,
      Reporting reporting) {
    public Selector {
      Objects.requireNonNull(kind);
    }

    boolean matches(NormalizedRequest request) {
      return switch (kind) {
        case COMMON -> true;
        case CAPABILITY -> request.capabilities().contains(capability);
        case PROVIDER -> request.messagingProvider().filter(value -> value == provider).isPresent();
        case RUNNER -> request.runner() == runner;
        case REPORTING -> request.reporting() == reporting;
      };
    }
  }

  public record Asset(String path, String content, boolean dependencyMetadata) {
    public Asset {
      Objects.requireNonNull(path);
      Objects.requireNonNull(content);
    }
  }

  public record ConfigurationClaim(
      String document, String pointer, MergeRule rule, String canonicalValue) {
    public ConfigurationClaim {
      Objects.requireNonNull(document);
      Objects.requireNonNull(pointer);
      Objects.requireNonNull(rule);
      Objects.requireNonNull(canonicalValue);
      if (!pointer.startsWith("/")) throw new IllegalArgumentException("JSON pointer is required");
    }
  }

  public record Contribution(
      String id,
      String blueprintVersion,
      Kind kind,
      Selector selector,
      int order,
      List<String> manifestIds,
      List<Asset> assets,
      List<ConfigurationClaim> configurationClaims) {
    public Contribution {
      Objects.requireNonNull(id);
      Objects.requireNonNull(blueprintVersion);
      Objects.requireNonNull(kind);
      Objects.requireNonNull(selector);
      if (selector.kind() != kind)
        throw new IllegalArgumentException("selector kind must match contribution kind");
      manifestIds = List.copyOf(manifestIds);
      assets = List.copyOf(assets);
      configurationClaims = List.copyOf(configurationClaims);
    }
  }

  public record StarterManifest(String groupId, Map<String, String> starters) {
    public StarterManifest {
      if (!matches(GROUP, groupId) || starters == null || starters.isEmpty()) {
        throw new IllegalArgumentException("a valid authoritative starter manifest is required");
      }
      starters = Map.copyOf(starters);
    }
  }

  public record Dependency(String groupId, String artifactId, String version) {}

  public record PlannedConfiguration(String contributionId, ConfigurationClaim claim) {}

  public record PlannedWrite(Path path, byte[] content, String sha256, boolean dependencyMetadata) {
    public PlannedWrite {
      content = content.clone();
    }

    @Override
    public byte[] content() {
      return content.clone();
    }
  }

  public record Diagnostic(
      String code,
      Phase phase,
      String contributionId,
      String target,
      String pointer,
      String message,
      String correctiveAction) {}

  public record CompositionPlan(
      NormalizedRequest request,
      String blueprintVersion,
      List<String> contributionIds,
      List<Dependency> dependencies,
      List<PlannedConfiguration> configuration,
      List<PlannedWrite> writes,
      List<Diagnostic> diagnostics) {
    public CompositionPlan {
      contributionIds = List.copyOf(contributionIds);
      dependencies = List.copyOf(dependencies);
      configuration = List.copyOf(configuration);
      writes = List.copyOf(writes);
      diagnostics = List.copyOf(diagnostics);
    }

    public boolean valid() {
      return diagnostics.isEmpty();
    }
  }

  public record WriteResult(WriteStatus status, String message) {}

  private record ClaimOwner(String contributionId, ConfigurationClaim claim) {}
}
