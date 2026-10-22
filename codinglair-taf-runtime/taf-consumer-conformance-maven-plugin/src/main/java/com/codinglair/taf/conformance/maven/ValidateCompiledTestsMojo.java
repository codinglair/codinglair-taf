package com.codinglair.taf.conformance.maven;

import java.io.IOException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

public final class ValidateCompiledTestsMojo extends AbstractConformanceMojo {
  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skipped()) return;
    try {
      var violations = CompiledTestConformance.validate(project, baseDirectory());
      if (!violations.isEmpty()) fail(String.join(System.lineSeparator(), violations));
      getLog().info("TAF compiled TestNG consumer conformance passed");
    } catch (MojoFailureException failure) {
      throw failure;
    } catch (IOException failure) {
      throw new MojoExecutionException("Unable to inspect compiled consumer tests", failure);
    }
  }
}
