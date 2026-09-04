package com.codinglair.taf.runtime.core.reporting.impl.allure;

import java.nio.file.Path;
import java.time.Duration;

/** Adapter-internal seam for a supported Allure single-file generator implementation. */
interface SingleFileReportGenerator {
  Path generate(GenerationRequest request);

  record GenerationRequest(
      Path resultsDirectory, Path stagingDirectory, String executable, Duration timeout) {}
}
