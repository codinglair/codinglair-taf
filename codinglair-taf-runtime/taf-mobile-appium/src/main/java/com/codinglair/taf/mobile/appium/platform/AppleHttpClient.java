package com.codinglair.taf.mobile.appium.platform;

import com.codinglair.taf.mobile.appium.configuration.AppleAuthentication.Mechanism;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.exception.AppleTransportFailure;
import com.codinglair.taf.mobile.appium.service.AppleTransportSecurity;
import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.core.security.ArtifactDownloadTransport;
import com.codinglair.taf.runtime.core.security.BoundedHttpBody;
import com.codinglair.taf.runtime.core.security.HttpTransportLogging;
import com.codinglair.taf.runtime.core.security.ResourceAccess;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.WebSocket;

/**
 * Credential injection below Selenium logging/capability retention; redirects are never followed.
 */
final class AppleHttpClient implements org.openqa.selenium.remote.http.HttpClient {
  private final AppleControllerSettings settings;
  private final AppleTransportSecurity security;
  private final String sessionId;
  private final HttpClient client;
  private final Duration timeout;

  AppleHttpClient(
      AppleControllerSettings settings, AppleTransportSecurity security, String sessionId) {
    this.settings = settings;
    this.security = security;
    this.sessionId = sessionId;
    HttpTransportLogging.requireSafe();
    timeout =
        List.of(
                settings.getReadinessTimeout(),
                settings.getCommandTimeout(),
                settings.getCleanupTimeout())
            .stream()
            .min(Duration::compareTo)
            .orElseThrow();
    client =
        HttpClient.newBuilder()
            .connectTimeout(settings.getReadinessTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Override
  public org.openqa.selenium.remote.http.HttpResponse execute(
      org.openqa.selenium.remote.http.HttpRequest request) {
    if (Thread.currentThread().isInterrupted())
      throw new IllegalStateException("Apple transport cancelled");
    var values = new ArrayList<String>();
    boolean connectionAttempted = false;
    try {
      security.requireSettings(settings);
      String base = settings.getServerUrl().toASCIIString().replaceAll("/+$", "");
      String path = request.getUri();
      if (!path.startsWith("/")
          || path.startsWith("//")
          || path.contains("..")
          || path.contains("?")
          || path.contains("#")) throw new SecurityException("Apple command destination denied");
      URI destination = URI.create(base + path);
      String body = Contents.string(request);
      Map<String, Object> payload =
          body.isBlank() ? new LinkedHashMap<>() : object(new Json().toType(body, Map.class));
      String action = path.substring(path.lastIndexOf('/') + 1).replace('_', '-');
      if (!action.matches("[a-z][a-z0-9-]{0,63}")) action = "command";
      inspectResources(payload, action);
      var headers = new LinkedHashMap<String, String>();
      var authentication = settings.getAuthentication();
      var references = authentication.getSecretReferences();
      if (authentication.getMechanism() == Mechanism.HEADER) {
        references.forEach((key, reference) -> headers.put(key, resolve(reference, values)));
      } else if (authentication.getMechanism() == Mechanism.BASIC) {
        String username = resolve(references.get("username"), values);
        String password = resolve(references.get("password"), values);
        if (username.contains(":")) throw new IllegalArgumentException();
        String credential =
            Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        values.add(credential);
        headers.put("Authorization", "Basic " + credential);
      }
      // Resolve configured nested credentials on every exchange for response scrubbing, but
      // inject them only into the new-session request. Never mutate the Selenium request.
      var nested = new LinkedHashMap<String, String>();
      collectReferences(settings.providerCapabilities(), nested, values);
      if (authentication.getMechanism() == Mechanism.PROVIDER_CAPABILITY)
        references.forEach((key, reference) -> nested.put(key, resolve(reference, values)));
      if (path.equals("/session") && request.getMethod().name().equals("POST")) {
        payload = object(replaceReferences(payload, nested));
        if (authentication.getMechanism() == Mechanism.PROVIDER_CAPABILITY) {
          var capabilities = object(payload.get("capabilities"));
          var always =
              capabilities.get("alwaysMatch") instanceof Map<?, ?> map
                  ? object(map)
                  : new LinkedHashMap<String, Object>();
          var first =
              capabilities.get("firstMatch") instanceof List<?> list
                  ? list.stream().map(AppleHttpClient::object).toList()
                  : List.<Map<String, Object>>of();
          nested.forEach(
              (pointer, value) -> {
                if (!pointer.startsWith("/")) return;
                String root = pointer.substring(1).split("/")[0];
                if (always.containsKey(root) || first.isEmpty()) inject(always, pointer, value);
                else first.forEach(branch -> inject(branch, pointer, value));
              });
          capabilities.put("alwaysMatch", always);
          if (!first.isEmpty()) capabilities.put("firstMatch", first);
          payload.put("capabilities", capabilities);
        }
        body = new Json().toJson(payload);
      }
      var wire =
          HttpRequest.newBuilder(destination)
              .timeout(timeout)
              .header("Content-Type", "application/json; charset=utf-8")
              .method(
                  request.getMethod().name(),
                  body.isBlank()
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(body));
      headers.forEach(
          (key, value) -> {
            if (value.chars().anyMatch(Character::isISOControl))
              throw new IllegalArgumentException();
            wire.header(key, value);
          });
      long deadline = System.nanoTime() + timeout.toNanos();
      connectionAttempted = true;
      var response = client.send(wire.build(), HttpResponse.BodyHandlers.ofInputStream());
      try (var input = response.body()) {
        if (response.statusCode() >= 300 && response.statusCode() < 400)
          throw new SecurityException("Apple command redirects are denied");
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new IOException();
        byte[] bytes = BoundedHttpBody.read(input, 4 * 1024 * 1024, Duration.ofNanos(remaining));
        Object decoded = new Json().toType(new String(bytes, StandardCharsets.UTF_8), Object.class);
        // Retrieve signed provider links inside the trusted boundary, before URL redaction.
        // The link never reaches the driver, collector, reporter or exception diagnostics.
        if (response.statusCode() < 400
            && path.endsWith("/stop_recording_screen")
            && settings.getAllowVisualArtifacts()
            && values.isEmpty()
            && decoded instanceof Map<?, ?> envelope
            && envelope.get("value") instanceof String link
            && (link.startsWith("https://") || link.startsWith("http://"))) {
          var downloader =
              new ArtifactDownloadTransport(
                  access -> {
                    try {
                      security.require(access.kind(), access.resource(), access.action());
                      return true;
                    } catch (SecurityException _) {
                      return false;
                    }
                  },
                  settings.getCleanupTimeout(),
                  2 * 1024 * 1024,
                  3);
          decoded =
              Map.of(
                  "value",
                  Base64.getEncoder()
                      .encodeToString(downloader.download(URI.create(link), Map.of())));
        }
        var safe = object(scrub(decoded, values));
        if (response.statusCode() >= 400) {
          var error =
              safe.get("value") instanceof Map<?, ?> map ? map.get("error") : "unknown error";
          String code =
              error instanceof String text
                      && List.of(
                              "session not created",
                              "stale element reference",
                              "invalid session id",
                              "no such element",
                              "timeout",
                              "unsupported operation")
                          .contains(text)
                  ? text
                  : "unknown error";
          safe =
              new LinkedHashMap<>(
                  Map.of(
                      "value",
                      Map.of(
                          "error",
                          code,
                          "message",
                          "Apple remote operation failed safely",
                          "stacktrace",
                          "")));
        } else if ((path.endsWith("/screenshot")
                || path.endsWith("/stop_recording_screen")
                || path.endsWith("/source"))
            && (!settings.getAllowVisualArtifacts() || !values.isEmpty())) {
          safe.put("value", "");
        }
        var result =
            new org.openqa.selenium.remote.http.HttpResponse().setStatus(response.statusCode());
        result.setHeader("Content-Type", "application/json; charset=utf-8");
        result.setContent(Contents.utf8String(new Json().toJson(safe)));
        return result;
      }
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      throw new AppleTransportFailure(connectionAttempted);
    } catch (IOException _) {
      // Retain only the cause type needed by session-uncertainty handling.
      throw new AppleTransportFailure(connectionAttempted);
    } catch (SecurityException _) {
      throw new AppleTransportFailure(connectionAttempted);
    } catch (RuntimeException _) {
      throw new AppleTransportFailure(connectionAttempted);
    } finally {
      values.clear();
    }
  }

  private void inspectResources(Object payload, String action) {
    switch (payload) {
      case Map<?, ?> map ->
          map.forEach(
              (key, value) -> {
                String name = key.toString().toLowerCase(Locale.ROOT).replace("appium:", "");
                if (value instanceof String resource
                    && List.of("app", "bundleid", "appid").contains(name))
                  security.require(ResourceAccess.Kind.APPLICATION, resource, action);
                if (value instanceof String resource
                    && List.of("serverurl", "webdriveragenturl").contains(name))
                  security.require(ResourceAccess.Kind.ENDPOINT, resource, action);
                if (value instanceof String resource && List.of("udid", "deviceid").contains(name))
                  security.require(
                      ResourceAccess.Kind.TARGET,
                      resource.equals(settings.getDeviceId())
                          ? AppleTransportSecurity.target(settings)
                          : resource,
                      "select");
                inspectResources(value, action);
              });
      case List<?> list -> list.forEach(item -> inspectResources(item, action));
      case null, default -> {}
    }
  }

  private String resolve(String reference, List<String> values) {
    String value = security.resolve(reference, sessionId, settings);
    values.add(value);
    return value;
  }

  private void collectReferences(
      Object value, Map<String, String> references, List<String> values) {
    switch (value) {
      case Map<?, ?> map -> {
        if (map.size() == 1 && map.get("secretReference") instanceof String reference)
          references.computeIfAbsent(reference, _ -> resolve(reference, values));
        else map.values().forEach(item -> collectReferences(item, references, values));
      }
      case List<?> list -> list.forEach(item -> collectReferences(item, references, values));
      case null, default -> {}
    }
  }

  private static Object replaceReferences(Object value, Map<String, String> references) {
    return switch (value) {
      case Map<?, ?> map -> {
        if (map.size() == 1 && map.get("secretReference") instanceof String reference) {
          if (!references.containsKey(reference)) throw new SecurityException();
          yield references.get(reference);
        }
        var result = new LinkedHashMap<String, Object>();
        map.forEach((key, item) -> result.put(key.toString(), replaceReferences(item, references)));
        yield result;
      }
      case List<?> list -> list.stream().map(item -> replaceReferences(item, references)).toList();
      case null, default -> value;
    };
  }

  private static void inject(Map<String, Object> capabilities, String pointer, String value) {
    var segments = pointer.substring(1).split("/");
    Map<String, Object> current = capabilities;
    for (int i = 0; i < segments.length - 1; i++) {
      Object existing = current.get(segments[i]);
      if (existing != null && !(existing instanceof Map<?, ?>)) throw new SecurityException();
      var next = existing == null ? new LinkedHashMap<String, Object>() : object(existing);
      current.put(segments[i], next);
      current = next;
    }
    if (current.containsKey(segments[segments.length - 1])) throw new SecurityException();
    current.put(segments[segments.length - 1], value);
  }

  private static Map<String, Object> object(Object value) {
    if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException();
    var result = new LinkedHashMap<String, Object>();
    map.forEach((key, item) -> result.put(key.toString(), item));
    return result;
  }

  private static Object scrub(Object value, List<String> secrets) {
    return switch (value) {
      case String text -> {
        String safe = text;
        for (String secret : secrets) {
          safe =
              safe.replace(secret, "[REDACTED]")
                  .replace(
                      new Json()
                          .toJson(secret)
                          .substring(1, new Json().toJson(secret).length() - 1),
                      "[REDACTED]");
        }
        yield new RedactionPipeline().redact(safe);
      }
      case Map<?, ?> map -> {
        var result = new LinkedHashMap<String, Object>();
        map.forEach(
            (key, item) -> {
              String name = key.toString();
              String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
              boolean sensitive =
                  List.of(
                          "password",
                          "token",
                          "secret",
                          "credential",
                          "accesskey",
                          "apikey",
                          "authorization",
                          "username")
                      .stream()
                      .anyMatch(normalized::contains);
              String safeName = (String) scrub(name, secrets);
              result.put(safeName, sensitive ? "[REDACTED]" : scrub(item, secrets));
            });
        yield result;
      }
      case List<?> list -> list.stream().map(item -> scrub(item, secrets)).toList();
      case null, default -> value;
    };
  }

  @Override
  public WebSocket openSocket(
      org.openqa.selenium.remote.http.HttpRequest request, WebSocket.Listener listener) {
    throw new UnsupportedOperationException(
        "Apple sockets require a separate authorized transport");
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsyncNative(
      HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    throw new UnsupportedOperationException("Native transport bypass is denied");
  }

  @Override
  public <T> HttpResponse<T> sendNative(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    throw new UnsupportedOperationException("Native transport bypass is denied");
  }

  @Override
  public void close() {
    client.close();
  }
}
