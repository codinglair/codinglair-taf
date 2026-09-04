import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dependency-free fail-closed contract checks for nightly and release verification. */
final class VerificationMatrixContractTest {
  private VerificationMatrixContractTest() {}

  public static void main(String[] args) throws Exception {
    String matrix = read(".github/workflows/verification-matrix.yml");
    String nightly = read(".github/workflows/nightly.yml");
    String release = read(".github/workflows/release.yml");
    String mobile = read(".github/workflows/mob-003-qualification.yml");

    require(nightly, "schedule:");
    require(nightly, "uses: ./.github/workflows/verification-matrix.yml");
    require(release, "workflow_dispatch:");
    require(release, "uses: ./.github/workflows/verification-matrix.yml");
    require(release, "java build-support/scripts/SyncDocVersion.java --check");
    require(matrix, "workflow_call:");
    require(matrix, "install --with-deps chromium firefox webkit");
    require(matrix, "-Dtest=PlaywrightBrowserSmokeTest");
    for (String suite :
        List.of(
            "environments",
            "mongodb-definitions",
            "database-migrations",
            "kafka",
            "rabbitmq",
            "jms",
            "virtualization")) {
      require(matrix, "suite: " + suite);
    }
    require(matrix, "max-parallel: 2");
    require(matrix, "fail-fast: false");
    require(matrix, "-Pmcp-e2e,security-it");
    require(matrix, "uses: ./.github/workflows/mob-003-qualification.yml");
    require(matrix, "name: Release verification gate");
    require(matrix, "if: ${{ always() }}");
    require(matrix, "test \"$result\" = success");
    require(matrix, "${{ github.run_id }}-${{ github.run_attempt }}");
    require(matrix, "comm -13");
    prohibit(matrix, "continue-on-error: true");
    prohibit(matrix, "max-attempts");
    prohibit(matrix, "android-device");
    require(mobile, "workflow_call:");
    require(mobile, "qualify.ps1 -Runs 1 -ControlledFailure");
    require(mobile, "if: ${{ always() }}");
    System.out.println("Nightly/release verification workflow contracts passed.");
  }

  private static String read(String path) throws Exception {
    return Files.readString(Path.of(path));
  }

  private static void require(String text, String required) {
    if (!text.contains(required)) {
      throw new AssertionError("Verification contract is missing: " + required);
    }
  }

  private static void prohibit(String text, String prohibited) {
    if (text.contains(prohibited)) {
      throw new AssertionError("Verification contract contains prohibited text: " + prohibited);
    }
  }
}
