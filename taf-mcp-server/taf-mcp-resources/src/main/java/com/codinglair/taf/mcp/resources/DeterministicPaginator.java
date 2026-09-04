package com.codinglair.taf.mcp.resources;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

final class DeterministicPaginator {
  private DeterministicPaginator() {}

  static <T> ResourcePage<T> page(
      List<T> orderedItems, ResourceQuery query, Function<T, String> searchableText) {
    var normalizedFilter = query.filter().toLowerCase(Locale.ROOT);
    var filtered =
        orderedItems.stream()
            .filter(
                item ->
                    normalizedFilter.isEmpty()
                        || searchableText
                            .apply(item)
                            .toLowerCase(Locale.ROOT)
                            .contains(normalizedFilter))
            .toList();
    var offset = decode(query.cursor(), normalizedFilter);
    if (offset > filtered.size()) {
      throw new IllegalArgumentException("cursor is outside the result set");
    }
    var end = Math.min(offset + query.pageSize(), filtered.size());
    var next = end < filtered.size() ? encode(end, normalizedFilter) : null;
    return new ResourcePage<>(filtered.subList(offset, end), java.util.Optional.ofNullable(next));
  }

  private static int decode(String cursor, String filter) {
    if (cursor == null) {
      return 0;
    }
    try {
      var decoded =
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":", -1);
      if (decoded.length != 3
          || !"v1".equals(decoded[0])
          || !fingerprint(filter).equals(decoded[2])) {
        throw new IllegalArgumentException("cursor does not match this query");
      }
      var offset = Integer.parseInt(decoded[1]);
      if (offset < 0) {
        throw new IllegalArgumentException("cursor offset is invalid");
      }
      return offset;
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("cursor is invalid", exception);
    }
  }

  private static String encode(int offset, String filter) {
    var value = "v1:" + offset + ":" + fingerprint(filter);
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String fingerprint(String value) {
    try {
      var digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest, 0, 8);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
