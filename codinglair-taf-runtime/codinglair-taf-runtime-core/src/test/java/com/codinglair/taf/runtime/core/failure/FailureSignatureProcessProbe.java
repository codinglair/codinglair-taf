package com.codinglair.taf.runtime.core.failure;

/** Child-JVM probe used by the Java 25 cross-process signature contract test. */
public final class FailureSignatureProcessProbe {
  private FailureSignatureProcessProbe() {}

  public static void main(String[] arguments) {
    RuntimeException failure = new RuntimeException(arguments[0]);
    failure.setStackTrace(new StackTraceElement[] {
        new StackTraceElement("sample.Controller", "execute", "Controller.java", Integer.parseInt(arguments[1]))});
    System.out.print(new FailureSignatureService().sign(
        FailureContext.of("web", "action", FailureContext.Boundary.UNKNOWN, failure)).value());
  }
}
