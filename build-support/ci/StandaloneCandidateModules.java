import java.util.LinkedHashSet;
import java.util.Set;

/** Selects reactor roots that must be installed before standalone consumer verification. */
public final class StandaloneCandidateModules {
  private static final String BOM = "codinglair-taf-bom";
  private static final String MOBILE_STARTER = "codinglair-taf-starter-mobile";
  private static final String CUCUMBER_RUNNER =
      "codinglair-taf-runtime/codinglair-taf-runner-cucumber";

  private StandaloneCandidateModules() {}

  static String select(String modules, String standaloneBuilds) {
    Set<String> selected = new LinkedHashSet<>();
    addCsv(selected, modules);
    for (String build : csv(standaloneBuilds)) {
      switch (build) {
        case "qualification/apple-simulator" -> {
          selected.add(BOM);
          selected.add(MOBILE_STARTER);
        }
        case "examples/apple-appium-consumer" -> {
          selected.add(BOM);
          selected.add(MOBILE_STARTER);
          selected.add(CUCUMBER_RUNNER);
        }
        default -> throw new IllegalArgumentException(
            "No candidate-module mapping exists for standalone build: " + build);
      }
    }
    return String.join(",", selected);
  }

  public static void main(String[] args) {
    String modules = null;
    String standaloneBuilds = null;
    for (String argument : args) {
      if (argument.startsWith("--modules=")) {
        modules = argument.substring("--modules=".length());
      } else if (argument.startsWith("--standalone-builds=")) {
        standaloneBuilds = argument.substring("--standalone-builds=".length());
      } else {
        throw new IllegalArgumentException("Unknown argument: " + argument);
      }
    }
    if (modules == null || standaloneBuilds == null) {
      throw new IllegalArgumentException("Expected reactor modules and standalone builds");
    }
    System.out.println(select(modules, standaloneBuilds));
  }

  private static void addCsv(Set<String> destination, String value) {
    destination.addAll(csv(value));
  }

  private static Set<String> csv(String value) {
    Set<String> values = new LinkedHashSet<>();
    if (value == null || value.isBlank()) {
      return values;
    }
    for (String entry : value.split(",")) {
      if (!entry.isBlank()) {
        values.add(entry.strip());
      }
    }
    return values;
  }
}
