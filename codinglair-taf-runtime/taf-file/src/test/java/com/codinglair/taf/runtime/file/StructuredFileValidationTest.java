package com.codinglair.taf.runtime.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.EnvironmentAccess;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Structured file validation controller")
class StructuredFileValidationTest {
  @TempDir Path root;

  @Nested
  @DisplayName("Format comparisons")
  class Formats {
    @Test
    @DisplayName("compares JSON deterministically with ignored fields and tolerance")
    void json() throws Exception {
      Path expected = write("expected.json", "{\"amount\":10.0,\"id\":1,\"ignored\":\"a\"}");
      Path actual = write("actual.json", "{\"ignored\":\"b\",\"id\":1,\"amount\":10.05}");
      var options =
          new FileComparisonOptions(
              Set.of("$.ignored"),
              new BigDecimal("0.1"),
              StandardCharsets.UTF_8,
              ',',
              List.of(),
              20);
      assertThat(compare(expected, actual, FileFormat.JSON, options).matched()).isTrue();
      assertThat(
              compare(expected, write("bad.json", "{\"id\":2}"), FileFormat.JSON, options)
                  .matched())
          .isFalse();
    }

    @Test
    @DisplayName("compares secure namespace-aware XML")
    void xml() throws Exception {
      Path expected = write("expected.xml", "<r><v id=\"1\">ok</v></r>");
      assertThat(
              compare(
                      expected,
                      write("same.xml", "<r><v id=\"1\">ok</v></r>"),
                      FileFormat.XML,
                      defaults())
                  .matched())
          .isTrue();
      assertThat(
              compare(
                      expected,
                      write("bad.xml", "<r><v id=\"2\">no</v></r>"),
                      FileFormat.XML,
                      defaults())
                  .matched())
          .isFalse();
    }

    @Test
    @DisplayName("compares quoted CSV cells")
    void csv() throws Exception {
      Path expected = write("expected.csv", "id,name\n1,\"Doe, Jane\"\n");
      assertThat(
              compare(
                      expected,
                      write("same.csv", "id,name\n1,\"Doe, Jane\"\n"),
                      FileFormat.CSV,
                      defaults())
                  .matched())
          .isTrue();
      assertThat(
              compare(expected, write("bad.csv", "id,name\n2,Jane\n"), FileFormat.CSV, defaults())
                  .matched())
          .isFalse();
    }

    @Test
    @DisplayName("compares fixed-width rows using explicit widths")
    void fixedWidth() throws Exception {
      var options =
          new FileComparisonOptions(
              Set.of(), BigDecimal.ZERO, StandardCharsets.UTF_8, ',', List.of(3, 2), 20);
      Path expected = write("expected.fw", "ABC12\nDEF34\n");
      assertThat(
              compare(expected, write("same.fw", "ABC12\nDEF34\n"), FileFormat.FIXED_WIDTH, options)
                  .matched())
          .isTrue();
      assertThat(
              compare(expected, write("bad.fw", "ABC99\nDEF34\n"), FileFormat.FIXED_WIDTH, options)
                  .matched())
          .isFalse();
    }

    @Test
    @DisplayName("compares Excel OOXML worksheet cells")
    void excel() throws Exception {
      Path expected = workbook("expected.xlsx", "42");
      assertThat(
              compare(expected, workbook("same.xlsx", "42"), FileFormat.EXCEL, defaults())
                  .matched())
          .isTrue();
      assertThat(
              compare(expected, workbook("bad.xlsx", "43"), FileFormat.EXCEL, defaults()).matched())
          .isFalse();
    }

    @Test
    @DisplayName("compares PDFs by bounded binary digest without content evidence")
    void pdf() throws Exception {
      Path expected = write("expected.pdf", "%PDF-1.7\nobject-a");
      var collector = collector();
      var controller = controller(collector, 1024);
      assertThat(
              controller
                  .compare(
                      new FileComparisonRequest(
                          expected,
                          write("same.pdf", "%PDF-1.7\nobject-a"),
                          FileFormat.PDF,
                          defaults()))
                  .matched())
          .isTrue();
      assertThat(
              controller
                  .compare(
                      new FileComparisonRequest(
                          expected,
                          write("bad.pdf", "%PDF-1.7\nobject-b"),
                          FileFormat.PDF,
                          defaults()))
                  .matched())
          .isFalse();
      assertThat(collector.getArtifacts())
          .allSatisfy(
              artifact ->
                  assertThat(artifact.content())
                      .doesNotContain("object-a", "object-b", root.toString()));
    }
  }

  @Nested
  @DisplayName("Security and lifecycle")
  class SecurityAndLifecycle {
    @Test
    @DisplayName("rejects traversal, symlinks, and oversized files without path disclosure")
    void pathAndSizePolicy() throws Exception {
      Path valid = write("valid.json", "{}");
      var controller = controller(collector(), 1);
      assertThatThrownBy(
              () ->
                  controller.compare(
                      new FileComparisonRequest(
                          Path.of("..", "outside.json"), valid, FileFormat.JSON, defaults())))
          .isInstanceOf(FileValidationException.class)
          .extracting("kind")
          .isEqualTo(FileValidationException.Kind.PATH_SAFETY);
      assertThatThrownBy(
              () ->
                  controller.compare(
                      new FileComparisonRequest(valid, valid, FileFormat.JSON, defaults())))
          .isInstanceOf(FileValidationException.class)
          .extracting("kind")
          .isEqualTo(FileValidationException.Kind.OVERSIZED);
    }

    @Test
    @DisplayName("is thread-safe for independent reads and closes idempotently")
    void concurrencyAndClose() throws Exception {
      Path expected = write("expected.json", "{\"value\":1}");
      Path actual = write("actual.json", "{\"value\":1}");
      var controller = controller(collector(), 1024);
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var tasks =
            java.util.stream.IntStream.range(0, 20)
                .mapToObj(
                    _ ->
                        (java.util.concurrent.Callable<Boolean>)
                            () ->
                                controller
                                    .compare(
                                        new FileComparisonRequest(
                                            expected, actual, FileFormat.JSON, defaults()))
                                    .matched())
                .toList();
        assertThat(executor.invokeAll(tasks))
            .allSatisfy(result -> assertThat(result.get()).isTrue());
      }
      controller.close();
      controller.close();
      assertThat(controller.state()).isEqualTo(ControllerState.CLOSED);
      assertThatThrownBy(
              () ->
                  controller.compare(
                      new FileComparisonRequest(expected, actual, FileFormat.JSON, defaults())))
          .isInstanceOf(FileValidationException.class)
          .extracting("kind")
          .isEqualTo(FileValidationException.Kind.LIFECYCLE);
    }
  }

  private FileValidationResult compare(
      Path expected, Path actual, FileFormat format, FileComparisonOptions options) {
    return controller(collector(), 1024 * 1024)
        .compare(new FileComparisonRequest(expected, actual, format, options));
  }

  private DefaultFileController controller(ArtifactCollector collector, long maximumSize) {
    var controller = new DefaultFileController("default", root, maximumSize);
    controller.initialize(
        new ControllerContext("session", EnvironmentAccess.unavailable(), collector));
    return controller;
  }

  private static ArtifactCollector collector() {
    return new ArtifactCollector(
        TafTest.of("file", StructuredFileValidationTest.class.getName()), "session", "test");
  }

  private Path write(String name, String content) throws Exception {
    return Files.writeString(root.resolve(name), content);
  }

  private static FileComparisonOptions defaults() {
    return FileComparisonOptions.defaults();
  }

  private Path workbook(String name, String value) throws Exception {
    Path path = root.resolve(name);
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
      zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
      zip.write(
          ("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\"><c r=\"A1\"><v>"
                  + value
                  + "</v></c></row></sheetData></worksheet>")
              .getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return path;
  }
}
