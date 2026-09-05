import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dependency-free equivalence and safety checks for Jenkins and GitLab reference pipelines. */
final class ReferencePipelineContractTest {
  private static final List<String> AUTHORITATIVE_GATES =
      List.of(
          "-Pruntime-gate",
          "-Pdependency-analysis,architecture,api-compatibility,schema-compatibility",
          "install --with-deps chromium firefox webkit",
          "-Dtest=PlaywrightBrowserSmokeTest",
          "codinglair-taf-runtime/taf-environments",
          "codinglair-taf-runtime/taf-test-definitions-mongodb",
          "codinglair-taf-runtime/taf-data-migration",
          "codinglair-taf-runtime/taf-messaging-kafka",
          "codinglair-taf-runtime/taf-messaging-rabbitmq",
          "codinglair-taf-runtime/taf-messaging-jms",
          "codinglair-taf-runtime/taf-virtualization-wiremock",
          "-Pmcp-e2e,security-it",
          "build.ps1 -AcceptAndroidSdkLicense -Clean",
          "qualify.ps1 -Runs 2",
          "qualify.ps1 -Runs 1 -ControlledFailure",
          "down --remove-orphans");

  private ReferencePipelineContractTest() {}

  public static void main(String[] args) throws Exception {
    String jenkins = Files.readString(Path.of("Jenkinsfile"));
    String gitlab = Files.readString(Path.of(".gitlab-ci.yml"));
    String github =
        Files.readString(Path.of(".github/workflows/verification-matrix.yml"))
            + Files.readString(Path.of(".github/workflows/mob-003-qualification.yml"));
    for (String gate : AUTHORITATIVE_GATES) {
      require(github, gate, "GitHub authoritative workflows");
      require(jenkins, gate, "Jenkinsfile");
      require(gitlab, gate, ".gitlab-ci.yml");
    }
    require(jenkins, "sh build-support/scripts/run-gitleaks.sh history", "Jenkinsfile");
    require(gitlab, "sh build-support/scripts/run-gitleaks.sh history", ".gitlab-ci.yml");
    require(
        jenkins,
        "java build-support/scripts/SyncDocVersion.java --check",
        "Jenkinsfile");
    require(
        gitlab,
        "java build-support/scripts/SyncDocVersion.java --check",
        ".gitlab-ci.yml");
    require(jenkins, "java25", "Jenkinsfile");
    require(jenkins, "MAVEN_OPTS", "Jenkinsfile");
    require(jenkins, "archiveArtifacts", "Jenkinsfile");
    require(gitlab, "tags: [linux, java25]", ".gitlab-ci.yml");
    require(gitlab, "resource_group: taf-container-verification", ".gitlab-ci.yml");
    require(gitlab, "when: always", ".gitlab-ci.yml");
    prohibit(jenkins, "credentials(", "Jenkinsfile");
    prohibit(gitlab, "CI_JOB_TOKEN:", ".gitlab-ci.yml");
    prohibit(gitlab, "DOCKER_AUTH_CONFIG:", ".gitlab-ci.yml");
    requireBalanced(jenkins, '{', '}', "Jenkinsfile");
    requireBalanced(jenkins, '(', ')', "Jenkinsfile");
    System.out.println("Jenkins and GitLab reference pipeline contracts passed.");
  }

  private static void require(String text, String required, String file) {
    if (!text.contains(required)) {
      throw new AssertionError(file + " is missing authoritative contract: " + required);
    }
  }

  private static void prohibit(String text, String prohibited, String file) {
    if (text.contains(prohibited)) {
      throw new AssertionError(file + " contains prohibited credential configuration: " + prohibited);
    }
  }

  private static void requireBalanced(String text, char opening, char closing, String file) {
    int depth = 0;
    for (char current : text.toCharArray()) {
      if (current == opening) {
        depth++;
      } else if (current == closing && --depth < 0) {
        throw new AssertionError(file + " has an unmatched " + closing);
      }
    }
    if (depth != 0) {
      throw new AssertionError(file + " has unmatched " + opening + " and " + closing);
    }
  }
}
