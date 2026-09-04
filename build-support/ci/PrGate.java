import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evaluates unconditional and path-selected GitHub job results for the stable PR gate. */
final class PrGate {
  private static final String SUCCESS = "success";
  private static final String SKIPPED = "skipped";
  private static final List<String> CONDITIONAL_LANES =
      List.of(
          "affected-verification", "cross-module-smoke", "browser-smoke", "appium-smoke");

  private PrGate() {}

  static void verify(Map<String, String> results, Map<String, Boolean> selected) {
    requireResult("change-impact", results.get("change-impact"), SUCCESS);
    requireResult("unit-tests", results.get("unit-tests"), SUCCESS);
    for (String lane : CONDITIONAL_LANES) {
      boolean expectedToRun = Boolean.TRUE.equals(selected.get(lane));
      requireResult(lane, results.get(lane), expectedToRun ? SUCCESS : SKIPPED);
    }
  }

  private static void requireResult(String lane, String actual, String expected) {
    if (!expected.equals(actual)) {
      throw new IllegalStateException(
          lane + " must be " + expected + " but reported " + String.valueOf(actual));
    }
  }

  public static void main(String[] args) {
    if (args.length != 10) {
      throw new IllegalArgumentException("Expected 10 gate arguments");
    }
    Map<String, String> results = new LinkedHashMap<>();
    results.put("change-impact", args[0]);
    results.put("unit-tests", args[1]);
    results.put("affected-verification", args[2]);
    results.put("cross-module-smoke", args[3]);
    results.put("browser-smoke", args[4]);
    results.put("appium-smoke", args[5]);
    Map<String, Boolean> selected = new LinkedHashMap<>();
    selected.put("affected-verification", Boolean.parseBoolean(args[6]));
    selected.put("cross-module-smoke", Boolean.parseBoolean(args[7]));
    selected.put("browser-smoke", Boolean.parseBoolean(args[8]));
    selected.put("appium-smoke", Boolean.parseBoolean(args[9]));
    verify(results, selected);
    System.out.println("All required pull-request lanes have acceptable terminal results.");
  }
}
