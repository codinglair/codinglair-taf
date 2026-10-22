package com.codinglair.taf.conformance.maven;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;

final class CompiledTestConformance {
  private static final String TESTNG_TEST = "org.testng.annotations.Test";
  private static final String JUNIT_PREFIX = "org.junit";
  private static final String BASE_TEST = "com.codinglair.taf.runtime.testng.TafBaseTest";
  private static final String CUCUMBER_RUNNER = "io.cucumber.testng.AbstractTestNGCucumberTests";
  private static final String CUCUMBER_OPTIONS = "io.cucumber.testng.CucumberOptions";
  private static final String FRAMEWORK_CUCUMBER_GLUE = "com.codinglair.taf.runtime.cucumber";
  private static final List<String> OWNED_LIFECYCLE_ANNOTATIONS =
      List.of(
          "org.testng.annotations.BeforeMethod",
          "org.testng.annotations.AfterMethod",
          "io.cucumber.java.Before",
          "io.cucumber.java.After");

  private CompiledTestConformance() {}

  static List<String> validate(MavenProject project, Path root) throws IOException {
    Path output = Path.of(project.getBuild().getTestOutputDirectory()).toAbsolutePath().normalize();
    try {
      return validate(
          output, project.getTestClasspathElements().stream().map(Path::of).toList(), root);
    } catch (DependencyResolutionRequiredException failure) {
      throw new IOException("test classpath was not resolved", failure);
    }
  }

  static List<String> validate(Path output, List<Path> classpath, Path root) throws IOException {
    if (!Files.isDirectory(output)) return List.of();
    var violations = new ArrayList<String>();
    try (var loader = classLoader(classpath)) {
      for (Path file : classFiles(output)) inspect(output, file, loader, violations);
    }
    if (hasCucumberClasses(output) && !hasCucumberSuite(root)) {
      violations.add(
          "suite.cucumber: Cucumber-TestNG requires a separate configured TestNG XML suite");
    }
    return List.copyOf(violations);
  }

  private static void inspect(Path output, Path file, ClassLoader loader, List<String> violations)
      throws IOException {
    String name = className(output, file);
    if (name.equals("module-info") || name.endsWith("package-info")) return;
    Class<?> type;
    try {
      type = Class.forName(name, false, loader);
    } catch (LinkageError | ClassNotFoundException failure) {
      violations.add(
          "compiled.unreadable: cannot inspect "
              + name
              + ": "
              + failure.getClass().getSimpleName());
      return;
    }
    boolean junit = annotations(type).stream().anyMatch(value -> value.startsWith(JUNIT_PREFIX));
    boolean testng = annotations(type).contains(TESTNG_TEST);
    for (var method : type.getDeclaredMethods()) {
      var annotations = annotations(method);
      junit |= annotations.stream().anyMatch(value -> value.startsWith(JUNIT_PREFIX));
      testng |= annotations.contains(TESTNG_TEST);
      if (annotations.stream().anyMatch(OWNED_LIFECYCLE_ANNOTATIONS::contains)) {
        violations.add(
            "lifecycle.consumer-owned: "
                + name
                + " declares framework-owned lifecycle annotation; use TafBaseTest/TafCucumberHooks");
      }
    }
    if (junit) violations.add("annotation.junit: compiled JUnit annotation found in " + name);
    if (testng && !BASE_TEST.equals(name) && !isAssignable(type, BASE_TEST)) {
      violations.add("integration.base-test: TestNG class must extend TafBaseTest: " + name);
    }
    if (isAssignable(type, CUCUMBER_RUNNER)
        && type.getSuperclass().getName().equals(CUCUMBER_RUNNER)) {
      violations.add(
          "integration.cucumber-runner: "
              + name
              + " must extend the framework Cucumber runner, not AbstractTestNGCucumberTests directly");
    }
    if (isAssignable(type, CUCUMBER_RUNNER) && !hasFrameworkCucumberGlue(type)) {
      violations.add(
          "integration.cucumber-hooks: "
              + name
              + " must register com.codinglair.taf.runtime.cucumber framework glue");
    }
  }

  private static boolean hasFrameworkCucumberGlue(Class<?> type) {
    try {
      for (var annotation : type.getDeclaredAnnotations()) {
        if (!annotation.annotationType().getName().equals(CUCUMBER_OPTIONS)) continue;
        Object value = annotation.annotationType().getMethod("glue").invoke(annotation);
        if (value instanceof String[] glue) {
          return java.util.Arrays.asList(glue).contains(FRAMEWORK_CUCUMBER_GLUE);
        }
      }
      return false;
    } catch (ReflectiveOperationException | LinkageError failure) {
      return false;
    }
  }

  private static List<String> annotations(java.lang.reflect.AnnotatedElement element) {
    var names = new ArrayList<String>();
    try {
      for (var annotation : element.getDeclaredAnnotations())
        names.add(annotation.annotationType().getName());
    } catch (TypeNotPresentException | LinkageError failure) {
      names.add("unresolved:" + failure.getMessage());
    }
    return names;
  }

  private static boolean isAssignable(Class<?> type, String parentName) {
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      if (current.getName().equals(parentName)) return true;
    }
    return false;
  }

  private static URLClassLoader classLoader(List<Path> classpath) {
    URL[] urls =
        classpath.stream()
            .map(Path::toUri)
            .map(
                uri -> {
                  try {
                    return uri.toURL();
                  } catch (IOException failure) {
                    throw new IllegalArgumentException(failure);
                  }
                })
            .toArray(URL[]::new);
    return new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
  }

  private static List<Path> classFiles(Path output) throws IOException {
    try (var files = Files.walk(output)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".class"))
          .toList();
    }
  }

  private static String className(Path output, Path file) {
    String relative = output.relativize(file).toString();
    return relative
        .substring(0, relative.length() - ".class".length())
        .replace('/', '.')
        .replace('\\', '.');
  }

  private static boolean hasCucumberClasses(Path output) throws IOException {
    for (Path file : classFiles(output)) {
      if (new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1)
          .contains("AbstractTestNGCucumberTests")) return true;
    }
    return false;
  }

  private static boolean hasCucumberSuite(Path root) throws IOException {
    Path resources = root.resolve("src/test/resources");
    if (!Files.isDirectory(resources)) return false;
    try (var files = Files.walk(resources)) {
      return files
          .filter(path -> path.toString().endsWith(".xml"))
          .map(
              path -> {
                try {
                  return Files.readString(path);
                } catch (IOException failure) {
                  return "";
                }
              })
          .anyMatch(text -> text.contains("cucumber") || text.contains("Cucumber"));
    }
  }
}
