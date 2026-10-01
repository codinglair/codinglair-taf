package com.codinglair.taf.mobile.appium.configuration;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded defensive JSON copying and duplicate ownership validation. */
final class JsonOptions {
  private JsonOptions() {}

  static Map<String, Object> copy(Map<String, Object> source) {
    if (source == null) throw invalid();
    return object(source, 0, new int[] {0});
  }

  private static Map<String, Object> object(Map<?, ?> source, int depth, int[] nodes) {
    var result = new LinkedHashMap<String, Object>();
    source.forEach(
        (key, value) -> {
          if (!(key instanceof String name) || name.isBlank() || name.length() > 256)
            throw invalid();
          String normalized = name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
          if (normalized.contains("password")
              || normalized.contains("token")
              || normalized.contains("secret")
              || normalized.contains("credential")
              || normalized.contains("accesskey")
              || normalized.contains("authorization"))
            throw new IllegalArgumentException(
                "Apple provider options cannot contain authentication values; use the authorized secret-reference boundary");
          result.put(name, value(value, depth + 1, nodes));
        });
    return Collections.unmodifiableMap(result);
  }

  private static Object value(Object source, int depth, int[] nodes) {
    if (depth > 16 || ++nodes[0] > 4096) throw invalid();
    return switch (source) {
      case null -> null;
      case String text -> {
        if (text.length() > 16384) throw invalid();
        yield text;
      }
      case Boolean flag -> flag;
      case Byte number -> number;
      case Short number -> number;
      case Integer number -> number;
      case Long number -> number;
      case BigInteger number -> number;
      case BigDecimal number -> number;
      case Double number -> {
        if (!Double.isFinite(number)) throw invalid();
        yield number;
      }
      case Float number -> {
        if (!Float.isFinite(number)) throw invalid();
        yield number;
      }
      case Map<?, ?> map -> object(map, depth, nodes);
      case List<?> list -> {
        var result = new ArrayList<Object>();
        list.forEach(item -> result.add(value(item, depth + 1, nodes)));
        yield Collections.unmodifiableList(result);
      }
      default -> throw invalid();
    };
  }

  static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> overlay) {
    var result = new LinkedHashMap<>(base);
    overlay.forEach(
        (key, value) -> {
          if (result.get(key) instanceof Map<?, ?> left && value instanceof Map<?, ?> right) {
            result.put(key, merge(copyObject(left), copyObject(right)));
          } else {
            if (result.containsKey(key) && !Objects.equals(result.get(key), value))
              throw new IllegalArgumentException(
                  "Apple provider option has conflicting duplicate ownership");
            result.put(key, value);
          }
        });
    return copy(result);
  }

  private static Map<String, Object> copyObject(Map<?, ?> source) {
    return object(source, 0, new int[] {0});
  }

  static void validateCapabilities(Map<String, Object> options) {
    Set<String> reserved =
        Set.of(
            "platformname",
            "browsername",
            "automationname",
            "devicename",
            "udid",
            "platformversion",
            "app",
            "bundleid",
            "newcommandtimeout",
            "autoacceptalerts",
            "autodismissalerts",
            "wdalocalport",
            "mjpegserverport",
            "deriveddatapath",
            "xcodeorgid",
            "xcodesigningid",
            "updatedwdabundleid",
            "useprebuiltwda",
            "usepreinstalledwda",
            "prebuiltwdapath",
            "webdriveragenturl");
    options.forEach(
        (key, value) -> {
          if (!key.matches("[A-Za-z][A-Za-z0-9_.-]*:[A-Za-z][A-Za-z0-9_.-]*")
              || key.toLowerCase(Locale.ROOT).startsWith("appium:"))
            throw new IllegalArgumentException(
                "Apple provider capabilities must have a provider namespace and cannot own Appium fields");
          inspect(value, reserved);
        });
  }

  private static void inspect(Object value, Set<String> reserved) {
    switch (value) {
      case Map<?, ?> map ->
          map.forEach(
              (key, item) -> {
                String name = key.toString().toLowerCase(Locale.ROOT);
                if (name.startsWith("appium:") || reserved.contains(name))
                  throw new IllegalArgumentException(
                      "Apple provider options cannot duplicate typed capability ownership");
                inspect(item, reserved);
              });
      case List<?> list -> list.forEach(item -> inspect(item, reserved));
      case null, default -> {}
    }
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Apple provider options must be bounded JSON values");
  }
}
