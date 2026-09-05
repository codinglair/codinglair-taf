import java.util.LinkedHashMap;
import java.util.Map;

/** Dependency-free regression checks for aggregate PR-gate semantics. */
final class PrGateTest {
  private PrGateTest() {}

  public static void main(String[] args) {
    acceptsSelectedSuccessAndLegitimateSkips();
    rejectsSelectedSkip();
    rejectsFailureAndCancellation();
    rejectsUnexpectedResult();
    System.out.println("Executed 8 aggregate PR-gate scenarios");
  }

  private static void acceptsSelectedSuccessAndLegitimateSkips() {
    PrGate.verify(
        results("success", "success", "success", "success", "skipped", "skipped", "success"),
        selected(true, false, false, true));
  }

  private static void rejectsSelectedSkip() {
    expectFailure(
        results("success", "success", "success", "skipped", "skipped", "skipped", "skipped"),
        selected(true, false, false, true));
  }

  private static void rejectsFailureAndCancellation() {
    expectFailure(
        results("success", "success", "failure", "skipped", "skipped", "skipped", "skipped"),
        selected(false, false, false, false));
    expectFailure(
        results("success", "success", "success", "cancelled", "skipped", "skipped", "skipped"),
        selected(true, false, false, false));
    expectFailure(
        results("success", "failure", "success", "skipped", "skipped", "skipped", "skipped"),
        selected(false, false, false, false));
    expectFailure(
        results("skipped", "success", "success", "skipped", "skipped", "skipped", "skipped"),
        selected(false, false, false, false));
  }

  private static void rejectsUnexpectedResult() {
    expectFailure(
        results("success", "success", "success", "unknown", "skipped", "skipped", "skipped"),
        selected(true, false, false, false));
    expectFailure(
        results("success", "success", "success", "success", "skipped", "skipped", "skipped"),
        selected(false, false, false, false));
    expectFailure(
        results("success", "success", "success", "skipped", "skipped", "skipped", ""),
        selected(false, false, false, false));
  }

  private static Map<String, String> results(String... values) {
    String[] names = {
      "secret-scanning",
      "change-impact",
      "unit-tests",
      "affected-verification",
      "cross-module-smoke",
      "browser-smoke",
      "appium-smoke"
    };
    var result = new LinkedHashMap<String, String>();
    for (int index = 0; index < names.length; index++) {
      result.put(names[index], values[index]);
    }
    return result;
  }

  private static Map<String, Boolean> selected(boolean... values) {
    String[] names = {
      "affected-verification", "cross-module-smoke", "browser-smoke", "appium-smoke"
    };
    var result = new LinkedHashMap<String, Boolean>();
    for (int index = 0; index < names.length; index++) {
      result.put(names[index], values[index]);
    }
    return result;
  }

  private static void expectFailure(Map<String, String> results, Map<String, Boolean> selected) {
    try {
      PrGate.verify(results, selected);
      throw new AssertionError("Invalid gate state was accepted");
    } catch (IllegalStateException expected) {
      // Expected fail-closed behavior.
    }
  }
}
