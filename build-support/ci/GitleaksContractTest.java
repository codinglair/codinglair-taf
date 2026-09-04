import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free safety and coverage checks for the repository Gitleaks contract. */
final class GitleaksContractTest {
  private GitleaksContractTest() {}

  public static void main(String[] args) throws Exception {
    String workflow = Files.readString(Path.of(".github/workflows/secret-scanning.yml"));
    String script = Files.readString(Path.of("build-support/scripts/run-gitleaks.sh"));
    String config = Files.readString(Path.of(".gitleaks.toml"));
    require(workflow, "pull_request:");
    require(workflow, "workflow_call:");
    require(workflow, "contents: read");
    require(workflow, "fetch-depth: 0");
    require(workflow, "persist-credentials: false");
    require(workflow, "timeout-minutes: 10");
    require(workflow, "github.event.pull_request.base.sha");
    require(workflow, "github.event.pull_request.head.sha");
    require(script, "ghcr.io/gitleaks/gitleaks:v8.30.1@sha256:");
    require(script, "git cat-file -e");
    require(script, "git rev-list");
    require(script, "${BASE_SHA}..${HEAD_SHA}");
    require(script, "--mount \"type=bind,source=$(pwd),target=/repo,readonly\"");
    require(script, "--network none");
    require(script, "--redact=100");
    require(script, "--config /repo/.gitleaks.toml");
    require(config, "useDefault = true");
    require(config, "reject-prior-api34\\.properties");
    require(config, "783a40134baf4f3012d4464fbe1571b1612a0dbd2e7a44d14bd8328923443833");
    require(config, "credential-reference=secret://env/ORDERS_API_TOKEN, outcome=FAILED");
    prohibit(workflow + script + config, "gitleaks/gitleaks-action");
    prohibit(workflow + script, "secrets.");
    prohibit(workflow + script, "privileged");
    prohibit(workflow + script, "report-path");
    System.out.println("Gitleaks workflow contract passed.");
  }

  private static void require(String text, String value) {
    if (!text.contains(value)) {
      throw new AssertionError("Gitleaks contract is missing: " + value);
    }
  }

  private static void prohibit(String text, String value) {
    if (text.contains(value)) {
      throw new AssertionError("Gitleaks contract contains prohibited content: " + value);
    }
  }
}
