package com.codinglair.taf.mcp.worker;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class WorkerPathNames {
  private WorkerPathNames() {}

  static String jobDirectory(String jobId) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(jobId.getBytes(StandardCharsets.UTF_8));
  }
}
