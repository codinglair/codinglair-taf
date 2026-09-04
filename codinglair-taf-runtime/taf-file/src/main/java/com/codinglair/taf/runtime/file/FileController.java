package com.codinglair.taf.runtime.file;

import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;

/** Session-owned bounded structured-file validation capability. */
public interface FileController extends TestController {
  @ControllerAction("Compare structured files")
  FileValidationResult compare(FileComparisonRequest request);
}
