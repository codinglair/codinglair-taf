package com.codinglair.taf.conformance.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Compiled consumer test conformance")
class CompiledTestConformanceTest {
  @TempDir Path temp;

  @Test
  @DisplayName("Rejects compiled JUnit annotations before test execution")
  void rejectsCompiledJunit() throws Exception {
    Path classes =
        compile(
            "org.junit.jupiter.api.Test",
            annotation("org.junit.jupiter.api"),
            "example.BadTest",
            badTest());

    assertThat(CompiledTestConformance.validate(classes, List.of(classes), temp))
        .anyMatch(value -> value.startsWith("annotation.junit"));
  }

  @Test
  @DisplayName("Accepts an ordinary TestNG class inheriting the framework base")
  void acceptsInheritedBaseTest() throws Exception {
    Path classes =
        compile(
            "org.testng.annotations.Test",
            annotation("org.testng.annotations"),
            "com.codinglair.taf.runtime.testng.TafBaseTest",
            "package com.codinglair.taf.runtime.testng; public abstract class TafBaseTest {}",
            "example.GoodTest",
            goodTest());

    assertThat(CompiledTestConformance.validate(classes, List.of(classes), temp)).isEmpty();
  }

  @Test
  @DisplayName("Rejects a Cucumber runner that omits framework hook glue")
  void rejectsMissingFrameworkCucumberGlue() throws Exception {
    Path classes =
        compile(
            "io.cucumber.testng.CucumberOptions",
            cucumberOptions(),
            "io.cucumber.testng.AbstractTestNGCucumberTests",
            "package io.cucumber.testng; public abstract class AbstractTestNGCucumberTests {}",
            "com.codinglair.taf.runtime.cucumber.AbstractCucumberRunner",
            "package com.codinglair.taf.runtime.cucumber; public abstract class AbstractCucumberRunner extends io.cucumber.testng.AbstractTestNGCucumberTests {}",
            "example.BadCucumberRunner",
            "package example; @io.cucumber.testng.CucumberOptions(glue={\"example\"}) public final class BadCucumberRunner extends com.codinglair.taf.runtime.cucumber.AbstractCucumberRunner {}");

    assertThat(CompiledTestConformance.validate(classes, List.of(classes), temp))
        .anyMatch(value -> value.startsWith("integration.cucumber-hooks"));
  }

  private Path compile(String... namesAndSources) throws Exception {
    Path sources = temp.resolve("sources");
    Path classes = temp.resolve("classes");
    Files.createDirectories(classes);
    var files = new java.util.ArrayList<String>();
    for (int index = 0; index < namesAndSources.length; index += 2) {
      Path file = sources.resolve(namesAndSources[index].replace('.', '/') + ".java");
      Files.createDirectories(file.getParent());
      Files.writeString(file, namesAndSources[index + 1]);
      files.add(file.toString());
    }
    var arguments = new java.util.ArrayList<String>();
    arguments.add("-d");
    arguments.add(classes.toString());
    arguments.addAll(files);
    assertThat(
            ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, arguments.toArray(String[]::new)))
        .isZero();
    return classes;
  }

  private static String annotation(String packageName) {
    return "package %s; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Test {}"
        .formatted(packageName);
  }

  private static String cucumberOptions() {
    return "package io.cucumber.testng; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface CucumberOptions { String[] glue() default {}; }";
  }

  private static String badTest() {
    return "package example; public class BadTest { @org.junit.jupiter.api.Test public void body() {} }";
  }

  private static String goodTest() {
    return "package example; public class GoodTest extends com.codinglair.taf.runtime.testng.TafBaseTest { @org.testng.annotations.Test public void body() {} }";
  }
}
