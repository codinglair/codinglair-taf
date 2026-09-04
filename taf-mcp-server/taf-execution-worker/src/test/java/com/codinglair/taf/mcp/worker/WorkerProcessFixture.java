package com.codinglair.taf.mcp.worker;

import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkerProcessFixture {
  private WorkerProcessFixture() {}

  public static void main(String[] arguments) throws Exception {
    switch (arguments[0]) {
      case "success" -> {
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "result.txt"), "worker-result");
        Files.writeString(Path.of("dirty.txt"), "worker-copy-change");
        System.out.print("build and test succeeded");
      }
      case "secret" -> {
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "result.txt"), "worker-canary-43f1");
        System.out.print("worker-canary-43f1");
      }
      case "large" -> System.out.print("x".repeat(32_768));
      case "child" -> Thread.sleep(60_000);
      case "tree" -> {
        var child =
            new ProcessBuilder(
                    javaExecutable(),
                    "-cp",
                    System.getProperty("java.class.path"),
                    WorkerProcessFixture.class.getName(),
                    "child")
                .start();
        System.out.println("CHILD=" + child.pid());
        System.out.flush();
        Thread.sleep(60_000);
      }
      default -> System.exit(19);
    }
  }

  private static String javaExecutable() {
    return Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java")
        .toString();
  }

  private static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }
}
