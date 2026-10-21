import com.codinglair.taf.conformance.ConformanceViolation;
import com.codinglair.taf.conformance.SutBoundaryVerifier;
import java.nio.file.Path;

/** Packaged public conformance client used by QE-132-001. */
public final class Qe132001ConformanceClient {
  private Qe132001ConformanceClient() {}

  public static void main(String[] arguments) {
    if (arguments.length != 2) throw new IllegalArgumentException("usage: <sutRoot> <automationRoot>");
    var report = new SutBoundaryVerifier().verify(Path.of(arguments[0]), Path.of(arguments[1]));
    for (ConformanceViolation violation : report.violations()) {
      System.out.println(
          violation.rule() + "|" + violation.message() + "|" + violation.location());
    }
    if (report.conforms()) throw new IllegalStateException("contaminated SUT unexpectedly conformed");
  }
}
