package com.codinglair.taf.mobile;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A provisioned device and Appium endpoint. Provisioning remains provider-owned. */
public record DeviceDescriptor(
    String id,
    MobilePlatform platform,
    DeviceMode mode,
    URI serverUri,
    Map<String, Object> capabilities) {
  public DeviceDescriptor {
    if (id == null || id.isBlank())
      throw new IllegalArgumentException("Device id must not be blank");
    Objects.requireNonNull(platform, "platform");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(serverUri, "serverUri");
    capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
    if (platform == MobilePlatform.IOS) {
      if (serverUri.getHost() == null
          || serverUri.getUserInfo() != null
          || serverUri.getRawQuery() != null
          || serverUri.getFragment() != null
          || !List.of("http", "https").contains(serverUri.getScheme()))
        throw new IllegalArgumentException(
            "Apple descriptor endpoint must be credential-free HTTP(S)");
      capabilities = copyAppleObject(capabilities, 0, new int[] {0});
    }
  }

  private static Map<String, Object> copyAppleObject(Map<?, ?> source, int depth, int[] nodes) {
    var result = new LinkedHashMap<String, Object>();
    source.forEach(
        (key, value) -> {
          if (!(key instanceof String name) || name.isBlank() || name.length() > 256)
            throw invalidApple();
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
                      "username",
                      "passphrase")
                  .stream()
                  .anyMatch(normalized::contains);
          sensitive |= Set.of("user", "pass", "pwd", "key", "auth").contains(normalized);
          if (sensitive && !reference(value)) throw invalidApple();
          result.put(name, copyApple(value, depth + 1, nodes));
        });
    return Collections.unmodifiableMap(result);
  }

  private static boolean reference(Object value) {
    return value instanceof Map<?, ?> map
        && map.size() == 1
        && map.get("secretReference") instanceof String text
        && text.length() <= 4096
        && !text.contains("..")
        && text.matches(
            "(?:credential://[a-z0-9][a-z0-9._/-]{0,255}|secret://(?:env/[A-Z_][A-Z0-9_]{0,127}|jasypt/[A-Za-z0-9+/=_-]{16,4000}))");
  }

  private static Object copyApple(Object value, int depth, int[] nodes) {
    if (depth > 16 || ++nodes[0] > 4096) throw invalidApple();
    return switch (value) {
      case Map<?, ?> map ->
          reference(map)
              ? Map.of("secretReference", map.get("secretReference"))
              : copyAppleObject(map, depth, nodes);
      case List<?> list -> {
        var result = new ArrayList<Object>();
        list.forEach(item -> result.add(copyApple(item, depth + 1, nodes)));
        yield Collections.unmodifiableList(result);
      }
      case String text -> {
        if (text.length() > 16384) throw invalidApple();
        yield text;
      }
      case Boolean flag -> flag;
      case Number number -> number;
      case null -> null;
      default -> throw invalidApple();
    };
  }

  private static IllegalArgumentException invalidApple() {
    return new IllegalArgumentException(
        "Apple descriptor requires bounded reference-only credentials");
  }
}
