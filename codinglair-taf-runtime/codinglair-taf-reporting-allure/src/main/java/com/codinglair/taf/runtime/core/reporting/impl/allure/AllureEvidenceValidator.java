package com.codinglair.taf.runtime.core.reporting.impl.allure;

import io.qameta.allure.internal.shadowed.jackson.databind.JsonNode;
import io.qameta.allure.internal.shadowed.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministically validates raw Allure 2 results and their standalone report. */
final class AllureEvidenceValidator {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern EMBEDDED_DATA =
      Pattern.compile("d\\('([^']+)','([A-Za-z0-9+/=]*)'\\)");

  public ValidationReport validate(Path results, Path report, Path matrix) {
    List<Violation> violations = new ArrayList<>();
    Map<String, ResultEvidence> actual = readResults(results, violations);
    List<AttachmentRef> containerAttachments =
        validateContainers(results, actual.keySet(), violations);
    validateAttachments(
        results,
        List.of(new ResultEvidence("containers", "containers", null, containerAttachments)),
        violations);
    validateOrphans(results, actual, containerAttachments, violations);
    JsonNode expectations = readJson(matrix, "matrix.invalid", violations);
    if (expectations != null) {
      validateExpectedResults(expectations, results, actual, violations);
      validateForbidden(expectations, results, report, violations);
      validateStandalone(expectations, results, report, actual, violations);
    }
    return new ValidationReport(
        violations.isEmpty(),
        actual.size(),
        referencedCount(actual) + containerAttachments.size(),
        violations);
  }

  private static Map<String, ResultEvidence> readResults(Path directory, List<Violation> out) {
    Map<String, ResultEvidence> results = new LinkedHashMap<>();
    if (!Files.isDirectory(directory)) {
      out.add(new Violation("results.missing", directory.toString()));
      return results;
    }
    try (var files = Files.list(directory)) {
      for (Path file :
          files
              .filter(path -> path.getFileName().toString().endsWith("-result.json"))
              .sorted()
              .toList()) {
        JsonNode root = readJson(file, "result.invalid", out);
        if (root == null) continue;
        String uuid = text(root, "uuid");
        if (uuid == null || uuid.isBlank()) {
          out.add(new Violation("result.uuid.missing", file.getFileName().toString()));
          continue;
        }
        if (results.containsKey(uuid)) {
          out.add(new Violation("result.uuid.duplicate", uuid));
          continue;
        }
        List<AttachmentRef> attachments = new ArrayList<>();
        collectAttachments(root.path("attachments"), uuid, null, attachments);
        collectStepAttachments(root.path("steps"), uuid, attachments);
        results.put(
            uuid, new ResultEvidence(uuid, text(root, "name"), text(root, "status"), attachments));
      }
    } catch (IOException failure) {
      out.add(new Violation("results.unreadable", safeMessage(failure)));
    }
    if (results.isEmpty()) out.add(new Violation("result.missing", "no *-result.json files"));
    validateAttachments(directory, results.values(), out);
    return results;
  }

  private static void collectStepAttachments(
      JsonNode steps, String owner, List<AttachmentRef> out) {
    if (!steps.isArray()) return;
    for (JsonNode step : steps) {
      String stepName = text(step, "name");
      collectAttachments(step.path("attachments"), owner, stepName, out);
      collectStepAttachments(step.path("steps"), owner, out);
    }
  }

  private static void collectAttachments(
      JsonNode nodes, String owner, String step, List<AttachmentRef> out) {
    if (!nodes.isArray()) return;
    for (JsonNode node : nodes) {
      out.add(
          new AttachmentRef(
              text(node, "name"), text(node, "source"), text(node, "type"), owner, step));
    }
  }

  private static void validateAttachments(
      Path directory, Iterable<ResultEvidence> results, List<Violation> out) {
    Set<String> sources = new HashSet<>();
    Path root = directory.toAbsolutePath().normalize();
    for (ResultEvidence result : results) {
      for (AttachmentRef ref : result.attachments()) {
        if (ref.source() == null || ref.source().isBlank()) {
          out.add(new Violation("attachment.source.missing", result.uuid()));
          continue;
        }
        if (!sources.add(ref.source()))
          out.add(new Violation("attachment.reference.duplicate", ref.source()));
        Path target = root.resolve(ref.source()).normalize();
        if (!target.startsWith(root) || Path.of(ref.source()).isAbsolute()) {
          out.add(new Violation("attachment.path.unsafe", ref.source()));
        } else if (!Files.isRegularFile(target)) {
          out.add(new Violation("attachment.dangling", ref.source()));
        }
      }
    }
  }

  private static List<AttachmentRef> validateContainers(
      Path directory, Set<String> resultIds, List<Violation> out) {
    Map<String, String> owners = new HashMap<>();
    List<AttachmentRef> attachments = new ArrayList<>();
    try (var files = Files.list(directory)) {
      for (Path file :
          files
              .filter(path -> path.getFileName().toString().endsWith("-container.json"))
              .sorted()
              .toList()) {
        JsonNode container = readJson(file, "container.invalid", out);
        if (container == null) continue;
        String containerId = text(container, "uuid");
        collectStepAttachments(container.path("befores"), containerId, attachments);
        collectStepAttachments(container.path("afters"), containerId, attachments);
        for (JsonNode child : container.path("children")) {
          String resultId = child.asText();
          if (!resultIds.contains(resultId))
            out.add(new Violation("container.child.dangling", resultId));
          String previous = owners.putIfAbsent(resultId, containerId);
          if (previous != null && !previous.equals(containerId))
            out.add(new Violation("result.owner.duplicate", resultId));
        }
      }
    } catch (IOException failure) {
      out.add(new Violation("container.unreadable", safeMessage(failure)));
    }
    return attachments;
  }

  private static void validateOrphans(
      Path directory,
      Map<String, ResultEvidence> results,
      List<AttachmentRef> containerAttachments,
      List<Violation> out) {
    Set<String> referenced = new HashSet<>();
    results
        .values()
        .forEach(result -> result.attachments().forEach(ref -> referenced.add(ref.source())));
    containerAttachments.forEach(ref -> referenced.add(ref.source()));
    try (var files = Files.list(directory)) {
      files
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(
              name ->
                  !name.endsWith(".json")
                      && !name.equals("categories.json")
                      && !name.equals("environment.properties"))
          .filter(name -> !referenced.contains(name))
          .sorted()
          .forEach(name -> out.add(new Violation("attachment.orphan", name)));
    } catch (IOException failure) {
      out.add(new Violation("results.unreadable", safeMessage(failure)));
    }
  }

  private static void validateExpectedResults(
      JsonNode matrix, Path directory, Map<String, ResultEvidence> actual, List<Violation> out) {
    Map<String, List<ResultEvidence>> byName = new HashMap<>();
    actual
        .values()
        .forEach(
            result ->
                byName.computeIfAbsent(result.name(), ignored -> new ArrayList<>()).add(result));
    for (JsonNode expected : matrix.path("expectedResults")) {
      String name = text(expected, "name");
      String status = text(expected, "status");
      List<ResultEvidence> matches =
          byName.getOrDefault(name, List.of()).stream()
              .filter(result -> status == null || status.equals(result.status()))
              .toList();
      int count = expected.path("count").asInt(1);
      if (matches.size() != count)
        out.add(
            new Violation(
                "result.count.mismatch",
                name + ": expected " + count + ", actual " + matches.size()));
      for (ResultEvidence result : matches) {
        validateExpectedAttachments(matrix, expected.path("attachments"), directory, result, out);
      }
      if (status != null && matches.isEmpty() && byName.containsKey(name)) {
        out.add(new Violation("result.status.mismatch", name + ": expected " + status));
      }
    }
    int expectedTotal = matrix.path("expectedResultCount").asInt(-1);
    if (expectedTotal >= 0 && actual.size() != expectedTotal)
      out.add(
          new Violation(
              "result.total.mismatch", "expected " + expectedTotal + ", actual " + actual.size()));
  }

  private static void validateExpectedAttachments(
      JsonNode matrix,
      JsonNode expected,
      Path directory,
      ResultEvidence result,
      List<Violation> out) {
    for (JsonNode item : expected) {
      String name = text(item, "name");
      List<AttachmentRef> matches =
          result.attachments().stream().filter(ref -> name.equals(ref.name())).toList();
      if (item.path("omitted").asBoolean(false)) {
        if (!matches.isEmpty())
          out.add(new Violation("attachment.omission.mismatch", result.name() + "/" + name));
        continue;
      }
      if (matches.size() != item.path("count").asInt(1)) {
        out.add(new Violation("attachment.count.mismatch", result.name() + "/" + name));
        continue;
      }
      for (AttachmentRef ref : matches) {
        String type = text(item, "contentType");
        String step = text(item, "ownerStep");
        if (type != null && !type.equals(ref.type()))
          out.add(new Violation("attachment.type.mismatch", ref.source()));
        if (step != null && !step.equals(ref.step()))
          out.add(new Violation("attachment.owner.mismatch", ref.source()));
        Path file = directory.resolve(ref.source()).normalize();
        if (!Files.isRegularFile(file)) continue;
        try {
          byte[] bytes = Files.readAllBytes(file);
          if (item.path("nonEmpty").asBoolean(true) && bytes.length == 0)
            out.add(new Violation("attachment.empty", ref.source()));
          long maximum = item.path("maxBytes").asLong(-1);
          if (maximum >= 0 && bytes.length > maximum)
            out.add(new Violation("attachment.oversized", ref.source()));
          String checksum = text(item, "sha256");
          if (checksum != null && !checksum.equals(sha256(bytes)))
            out.add(new Violation("attachment.checksum.mismatch", ref.source()));
          String content = new String(bytes, StandardCharsets.UTF_8);
          for (JsonNode field : matrix.path("requiredFields")) {
            if (!content.contains(field.asText() + "="))
              out.add(
                  new Violation("attachment.field.missing", ref.source() + ": " + field.asText()));
          }
          for (JsonNode field : item.path("requiredFields")) {
            if (!content.contains(field.asText() + "="))
              out.add(
                  new Violation("attachment.field.missing", ref.source() + ": " + field.asText()));
          }
        } catch (IOException failure) {
          out.add(new Violation("attachment.unreadable", ref.source()));
        }
      }
    }
  }

  private static void validateForbidden(
      JsonNode matrix, Path results, Path report, List<Violation> out) {
    for (JsonNode value : matrix.path("forbiddenPatterns")) {
      String pattern = value.asText();
      if (contains(results, pattern) || (report != null && contains(report, pattern)))
        out.add(new Violation("content.unsanitized", pattern));
    }
  }

  private static void validateStandalone(
      JsonNode matrix,
      Path results,
      Path report,
      Map<String, ResultEvidence> actual,
      List<Violation> out) {
    if (report == null || !Files.isRegularFile(report)) {
      out.add(new Violation("report.missing", String.valueOf(report)));
      return;
    }
    String html = readText(report, out);
    if (html == null || !html.toLowerCase(Locale.ROOT).contains("<html"))
      out.add(new Violation("report.invalid", report.toString()));
    for (JsonNode marker : matrix.path("standaloneRequiredMarkers")) {
      if (html == null || !html.contains(marker.asText()))
        out.add(new Violation("report.attachment.unavailable", marker.asText()));
    }
    if (html != null) {
      validateEmbeddedAttachments(results, html, actual, out);
    }
  }

  private static void validateEmbeddedAttachments(
      Path results, String html, Map<String, ResultEvidence> actual, List<Violation> out) {
    Map<String, byte[]> embedded = new HashMap<>();
    var matcher = EMBEDDED_DATA.matcher(html);
    while (matcher.find()) {
      try {
        embedded.put(matcher.group(1), Base64.getDecoder().decode(matcher.group(2)));
      } catch (IllegalArgumentException failure) {
        out.add(new Violation("report.data.invalid", matcher.group(1)));
      }
    }
    List<JsonNode> testCases =
        embedded.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("data/test-cases/"))
            .map(entry -> readEmbeddedJson(entry.getKey(), entry.getValue(), out))
            .filter(java.util.Objects::nonNull)
            .toList();
    for (ResultEvidence result : actual.values()) {
      for (AttachmentRef ref : result.attachments()) {
        EmbeddedAttachment found =
            findEmbedded(testCases, result.name(), result.status(), ref.name(), ref.type());
        if (found == null) {
          out.add(new Violation("report.attachment.unavailable", result.name() + "/" + ref.name()));
          continue;
        }
        byte[] reportBytes = embedded.get("data/attachments/" + found.source());
        if (reportBytes == null) {
          out.add(new Violation("report.attachment.unavailable", found.source()));
          continue;
        }
        try {
          byte[] resultBytes = Files.readAllBytes(results.resolve(ref.source()).normalize());
          if (!java.util.Arrays.equals(resultBytes, reportBytes))
            out.add(new Violation("report.attachment.content.mismatch", ref.source()));
        } catch (IOException failure) {
          out.add(new Violation("attachment.unreadable", ref.source()));
        }
      }
    }
  }

  private static JsonNode readEmbeddedJson(String name, byte[] bytes, List<Violation> out) {
    try {
      return JSON.readTree(bytes);
    } catch (IOException failure) {
      out.add(new Violation("report.data.invalid", name));
      return null;
    }
  }

  private static EmbeddedAttachment findEmbedded(
      List<JsonNode> testCases,
      String resultName,
      String resultStatus,
      String attachmentName,
      String contentType) {
    for (JsonNode testCase : testCases) {
      if (!java.util.Objects.equals(resultName, text(testCase, "name"))
          || !java.util.Objects.equals(resultStatus, text(testCase, "status"))) continue;
      List<JsonNode> attachments = new ArrayList<>();
      collectEmbedded(testCase.path("beforeStages"), attachments);
      collectEmbedded(testCase.path("testStage"), attachments);
      collectEmbedded(testCase.path("afterStages"), attachments);
      for (JsonNode attachment : attachments) {
        if (attachmentName.equals(text(attachment, "name"))
            && contentType.equals(text(attachment, "type"))) {
          return new EmbeddedAttachment(text(attachment, "source"));
        }
      }
    }
    return null;
  }

  private static void collectEmbedded(JsonNode node, List<JsonNode> out) {
    if (node.isArray()) {
      node.forEach(child -> collectEmbedded(child, out));
      return;
    }
    if (!node.isObject()) return;
    node.path("attachments").forEach(out::add);
    collectEmbedded(node.path("steps"), out);
  }

  private static boolean contains(Path path, String text) {
    if (Files.isDirectory(path)) {
      try (var files = Files.walk(path)) {
        return files.filter(Files::isRegularFile).anyMatch(file -> contains(file, text));
      } catch (IOException ignored) {
        return false;
      }
    }
    try {
      return Files.readString(path, StandardCharsets.UTF_8).contains(text);
    } catch (IOException ignored) {
      return false;
    }
  }

  private static JsonNode readJson(Path file, String code, List<Violation> out) {
    try {
      return JSON.readTree(file.toFile());
    } catch (IOException failure) {
      out.add(new Violation(code, file + ": " + safeMessage(failure)));
      return null;
    }
  }

  private static String readText(Path file, List<Violation> out) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException failure) {
      out.add(new Violation("report.unreadable", safeMessage(failure)));
      return null;
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asText();
  }

  private static int referencedCount(Map<String, ResultEvidence> results) {
    return results.values().stream().mapToInt(result -> result.attachments().size()).sum();
  }

  private static String safeMessage(Exception failure) {
    return failure.getClass().getSimpleName();
  }

  private static String sha256(byte[] content) {
    try {
      return Base64.getEncoder()
          .encodeToString(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (Exception failure) {
      throw new IllegalStateException("SHA-256 is unavailable", failure);
    }
  }

  public record ValidationReport(
      boolean valid, int resultCount, int attachmentCount, List<Violation> violations) {
    public ValidationReport {
      violations = List.copyOf(violations);
    }

    public String toJson() {
      try {
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(this);
      } catch (IOException failure) {
        throw new IllegalStateException("Could not serialize validation report", failure);
      }
    }
  }

  public record Violation(String code, String detail) {}

  private record ResultEvidence(
      String uuid, String name, String status, List<AttachmentRef> attachments) {}

  private record AttachmentRef(
      String name, String source, String type, String owner, String step) {}

  private record EmbeddedAttachment(String source) {}
}
