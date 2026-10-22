package com.codinglair.taf.conformance.maven;

import java.nio.file.Path;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

abstract class AbstractConformanceMojo extends AbstractMojo {
  protected MavenProject project;

  private boolean skip;

  protected final boolean skipped() {
    if (skip) getLog().warn("TAF consumer conformance was explicitly skipped");
    return skip;
  }

  protected final Path baseDirectory() {
    return project.getBasedir().toPath().toAbsolutePath().normalize();
  }

  protected final void fail(String message) throws MojoFailureException {
    throw new MojoFailureException("TAF-CONSUMER-CONFORMANCE: " + message);
  }
}
