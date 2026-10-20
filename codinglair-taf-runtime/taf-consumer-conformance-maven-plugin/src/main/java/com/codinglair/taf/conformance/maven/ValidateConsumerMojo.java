package com.codinglair.taf.conformance.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

public final class ValidateConsumerMojo extends AbstractConformanceMojo {
  private MavenSession session;
  private MavenPluginManager pluginManager;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skipped()) return;
    try {
      var violations =
          ConsumerBuildConformance.validate(project, session, pluginManager, baseDirectory());
      if (!violations.isEmpty()) fail(String.join(System.lineSeparator(), violations));
      getLog().info("TAF effective TestNG provider conformance passed");
    } catch (MojoFailureException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new MojoExecutionException("Unable to inspect the effective consumer build", failure);
    }
  }
}
