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
        "      - qe-132-002-qualification",
        "      - cross-module-smoke",
        "      - browser-smoke",
        "      - appium-smoke");
    require(yaml, "name: Pull Request Verification");
    require(yaml, "name: PR gate");
    require(yaml, "uses: ./.github/workflows/secret-scanning.yml");
    require(yaml, "uses: ./.github/workflows/aws-capability.yml");
    require(yaml, "aws: ${{ steps.classify.outputs.aws }}");
    require(yaml, "standalone_builds: ${{ steps.classify.outputs.standalone_builds }}");
    require(yaml, "qe132002: ${{ steps.classify.outputs.qe132002 }}");
    String affectedVerification =
        job(yaml, "  affected-verification:", "  qe-132-002-qualification:");
    requireInOrder(
        affectedVerification,
        "standalone_builds='${{ needs.change-impact.outputs.standalone_builds }}'",
        "goal=install",
        "modules=\"$(java build-support/ci/StandaloneCandidateModules.java --modules=\"$modules\" --standalone-builds=\"$standalone_builds\")\"",
        "./mvnw $MAVEN_ARGS -pl \"$modules\" -am \"$goal\"",
        "TAF_VERSION=\"$(./mvnw -B -ntp -Dstyle.color=never help:evaluate -Dexpression=revision -q -DforceStdout)\"",
        "./mvnw $MAVEN_ARGS -f \"$build/pom.xml\" -Dtaf.version=\"$TAF_VERSION\" verify");
    prohibit(affectedVerification, "release/qe-132-002");
    String qeQualification =
        job(yaml, "  qe-132-002-qualification:", "  cross-module-smoke:");
    requireInOrder(
        qeQualification,
        "if: needs.change-impact.outputs.qe132002 == 'true'",
        "java-version: '25'",
        "docker build --tag codinglair/taf-test-runner:qe-132-002 containers/taf-test-runner",
        "test \"$(target/qe-132-002-tooling/allure-2.36.0/bin/allure --version)\" = '2.36.0'",
        "shell: pwsh",
        "./build-support/verification/qe-132-002.ps1",
        "if: ${{ always() }}",
        "target/qe-132-002/candidate-identity.json",
        "target/qe-132-002/summary.json",
        "uses: actions/upload-artifact@v4");
    prohibit(qeQualification, "-SkipStage");
    prohibit(qeQualification, "continue-on-error");
    require(yaml, "${{ needs.qe-132-002-qualification.result }}");
    require(yaml, "${{ needs.change-impact.outputs.qe132002 }}");
    verifyStandaloneCandidateSelection();
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

  private static void verifyStandaloneCandidateSelection() throws Exception {
    Process process =
        new ProcessBuilder(
                "java",
                "build-support/ci/StandaloneCandidateModules.java",
                "--modules=",
                "--standalone-builds=qualification/apple-simulator")
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes()).strip();
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new AssertionError("Standalone candidate selection failed: " + output);
    }
    String expected = "codinglair-taf-bom,codinglair-taf-starter-mobile";
    if (!output.equals(expected)) {
      throw new AssertionError(
          "Empty-module Apple simulator selection must be " + expected + " but was " + output);
    }
    verifyCandidateReactor(output);

    String sutSelection =
        StandaloneCandidateModules.select(
            "codinglair-taf-runtime/codinglair-taf-reporting-allure",
            "release/sut-boundary/boot3,release/sut-boundary/boot4");
    String expectedSutSelection =
        "codinglair-taf-runtime/codinglair-taf-reporting-allure";
    if (!sutSelection.equals(expectedSutSelection)) {
      throw new AssertionError(
          "Standalone SUT selection must retain only reactor modules but was " + sutSelection);
    }
  }

  private static void verifyCandidateReactor(String modules) throws Exception {
    String wrapper =
        System.getProperty("os.name").startsWith("Windows") ? ".\\mvnw.cmd" : "./mvnw";
    List<String> command =
        List.of(
            wrapper,
            "-B",
            "-ntp",
            "-Dstyle.color=never",
            "-pl",
            modules,
            "-am",
            "validate");
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes());
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new AssertionError("Candidate reactor validation failed: " + output);
    }
    for (String project :
        List.of(
            "Codinglair TAF Community BOM",
            "TAF Runtime Core",
            "TAF Mobile Core",
            "TAF Mobile Appium",
            "Codinglair TAF Mobile Starter")) {
      if (!output.contains(project)) {
        throw new AssertionError("Candidate reactor is missing " + project + ": " + output);
      }
    }
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
