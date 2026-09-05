import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dependency-free structural validation for the repository's GitHub PR workflow. */
final class WorkflowContractTest {
  private WorkflowContractTest() {}

  public static void main(String[] args) throws Exception {
    Path workflow = Path.of(".github/workflows/pull-request.yml");
    List<String> lines = Files.readAllLines(workflow);
    String yaml = String.join("\n", lines);
    validateBasicYamlStructure(lines);
    requireInOrder(
        yaml,
        "  appium-smoke:",
        "      - name: Reclaim runner space for the ephemeral Android image",
        "      - name: Enable Docker containerd image store",
        "      - name: Set up attestation-capable Docker Buildx",
        "      - name: Capture disk state before MOB-003 build",
        "      - name: Verify approved MOB-003 recipe and evidence references",
        "      - name: Build MOB-003 Android emulator image locally",
        "      - name: Capture disk state after MOB-003 build",
        "      - name: Preflight Docker, Compose, and KVM",
        "      - name: Verify KVM inside emulator image",
        "      - name: Start Android emulator and Appium",
        "      - name: Run real Android emulator smoke",
        "      - name: Prove emulator smoke executed",
        "      - name: Capture mobile failure diagnostics",
        "      - name: Tear down Android emulator and Appium",
        "      - name: Publish mobile failure artifacts");
    requireInOrder(
        yaml,
        "  pr-gate:",
        "    if: ${{ always() }}",
        "    needs:",
        "      - secret-scanning",
        "      - change-impact",
        "      - unit-tests",
        "      - affected-verification",
        "      - cross-module-smoke",
        "      - browser-smoke",
        "      - appium-smoke");
    require(yaml, "name: Pull Request Verification");
    require(yaml, "name: PR gate");
    require(yaml, "uses: ./.github/workflows/secret-scanning.yml");
    require(yaml, "name: Documentation version");
    require(yaml, "java build-support/scripts/SyncDocVersion.java --check");
    require(yaml, "- documentation-version");
    require(yaml, "java-version: '25'");
    require(yaml, "runs-on: ubuntu-24.04");
    require(yaml, "timeout-minutes: 30");
    require(yaml, "TAF_ANDROID_APPIUM_URL: http://127.0.0.1:4724");
    require(yaml, "./mvnw -pl codinglair-taf-runtime/taf-mobile-appium -am verify -Pandroid-emulator");
    String appiumSmoke = job(yaml, "  appium-smoke:", "  pr-gate:");
    requireInOrder(
        appiumSmoke,
        "uses: docker/setup-docker-action@v5",
        "\"containerd-snapshotter\": true",
        "uses: docker/setup-buildx-action@v4",
        "containers/android-emulator/google/verify.ps1",
        "containers/android-emulator/google/build.ps1");
    require(appiumSmoke, "containers/android-emulator/google/build.ps1");
    requireInOrder(
        appiumSmoke,
        "      - name: Verify approved MOB-003 recipe and evidence references",
        "-ExecutionPolicy Bypass",
        "-File containers/android-emulator/google/verify.ps1",
        "      - name: Build MOB-003 Android emulator image locally",
        "-ExecutionPolicy Bypass",
        "-File containers/android-emulator/google/build.ps1");
    require(appiumSmoke, "-AcceptAndroidSdkLicense");
    require(appiumSmoke, "-Clean");
    require(appiumSmoke, "-Image codinglair-taf/android-emulator:mob-003-api34");
    require(appiumSmoke, "sudo rm -rf --");
    require(appiumSmoke, "/usr/local/lib/android");
    require(appiumSmoke, "/opt/ghc");
    require(appiumSmoke, "/usr/local/.ghcup");
    require(appiumSmoke, "/usr/share/dotnet");
    require(appiumSmoke, "df -h");
    require(appiumSmoke, "docker system df");
    require(appiumSmoke, "docker buildx ls");
    require(appiumSmoke, "docker buildx inspect");
    requireInOrder(
        appiumSmoke,
        "      - name: Capture disk state before MOB-003 build",
        "      - name: Build MOB-003 Android emulator image locally",
        "      - name: Capture disk state after MOB-003 build",
        "if: ${{ always() }}");
    require(appiumSmoke, "containers/android-emulator/google/verify-compose.ps1");
    requireInOrder(
        appiumSmoke,
        "      - name: Preflight Docker, Compose, and KVM",
        "-ExecutionPolicy Bypass",
        "-File containers/android-emulator/google/verify-compose.ps1");
    require(appiumSmoke, "--file containers/android-emulator/google/compose.qualify.yaml");
    require(appiumSmoke, "compose_file=\"containers/android-emulator/google/compose.qualify.yaml\"");
    require(appiumSmoke, "compose_args=(--file containers/android-emulator/google/compose.qualify.yaml)");
    requireInOrder(
        appiumSmoke,
        "      - name: Verify KVM inside emulator image",
        "--file containers/android-emulator/google/compose.qualify.yaml",
        "      - name: Start Android emulator and Appium",
        "--file containers/android-emulator/google/compose.qualify.yaml",
        "      - name: Capture mobile failure diagnostics",
        "compose_file=\"containers/android-emulator/google/compose.qualify.yaml\"",
        "      - name: Tear down Android emulator and Appium",
        "compose_args=(--file containers/android-emulator/google/compose.qualify.yaml)");
    require(appiumSmoke, "adb -s 127.0.0.1:5557 shell getprop sys.boot_completed");
    require(appiumSmoke, "TAF_ANDROID_DEVICE_ID: android-emulator:5555");
    prohibit(appiumSmoke, "containers/android-emulator/compose.yaml");
    prohibit(appiumSmoke, "budtmo/docker-android");
    prohibit(appiumSmoke, "emulator-5554");
    prohibit(appiumSmoke, "docker push");
    prohibit(appiumSmoke, "docker pull");
    prohibit(appiumSmoke, "docker save");
    prohibit(appiumSmoke, "docker export");
    prohibit(appiumSmoke, "actions/cache");
    require(yaml, "if: ${{ failure() }}");
    require(yaml, "if: ${{ always() }}");
    require(yaml, "WorkflowYamlParse .github/workflows/pull-request.yml");
    require(yaml, "java -cp target/ci-support ReferencePipelineContractTest");
    require(yaml, "WorkflowYamlParse .gitlab-ci.yml");
    require(yaml, "java -cp target/ci-support ImagePolicyTest");
    if (yaml.contains("pull_request_target") || yaml.contains("privileged: true")) {
      throw new AssertionError("Workflow contains a prohibited privilege boundary");
    }
    System.out.println("GitHub PR workflow structural contract passed.");
  }

  private static void validateBasicYamlStructure(List<String> lines) {
    validateBasicYamlStructure(lines, "name: Pull Request Verification");
  }

  private static void validateBasicYamlStructure(List<String> lines, String expectedName) {
    boolean hasName = false;
    boolean hasOn = false;
    boolean hasJobs = false;
    for (int index = 0; index < lines.size(); index++) {
      String line = lines.get(index);
      if (line.indexOf('\t') >= 0) {
        throw new AssertionError("YAML tabs are prohibited at line " + (index + 1));
      }
      int indentation = line.length() - line.stripLeading().length();
      if (!line.isBlank() && indentation % 2 != 0) {
        throw new AssertionError("YAML indentation must use two-space levels at line " + (index + 1));
      }
      hasName |= line.equals(expectedName);
      hasOn |= line.equals("on:");
      hasJobs |= line.equals("jobs:");
    }
    if (!hasName || !hasOn || !hasJobs) {
      throw new AssertionError("Workflow is missing a required top-level mapping");
    }
  }

  private static void require(String text, String required) {
    if (!text.contains(required)) {
      throw new AssertionError("Workflow is missing: " + required);
    }
  }

  private static void prohibit(String text, String prohibited) {
    if (text.contains(prohibited)) {
      throw new AssertionError("Workflow contains prohibited content: " + prohibited);
    }
  }

  private static String job(String yaml, String start, String end) {
    int startIndex = yaml.indexOf(start);
    int endIndex = yaml.indexOf(end, startIndex + start.length());
    if (startIndex < 0 || endIndex < 0) {
      throw new AssertionError("Workflow job boundary is missing: " + start);
    }
    return yaml.substring(startIndex, endIndex);
  }

  private static void requireInOrder(String text, String... required) {
    int previous = -1;
    for (String value : required) {
      int current = text.indexOf(value, previous + 1);
      if (current < 0 || current <= previous) {
        throw new AssertionError("Workflow item is absent or out of order: " + value);
      }
      previous = current;
    }
  }
}
