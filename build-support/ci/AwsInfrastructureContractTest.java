import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dependency-free fail-closed checks for the INF-110-001 AWS CI contract. */
final class AwsInfrastructureContractTest {
  private AwsInfrastructureContractTest() {}

  public static void main(String[] args) throws Exception {
    String workflow = Files.readString(Path.of(".github/workflows/aws-capability.yml"));
    String pullRequest = Files.readString(Path.of(".github/workflows/pull-request.yml"));
    String matrix = Files.readString(Path.of(".github/workflows/verification-matrix.yml"));
    String dependencyRecord =
        Files.readString(Path.of("docs/architecture/decisions/AWS-110-001-dependency-compatibility.md"));
    String operations = Files.readString(Path.of("docs/operations/aws-capability-ci.md"));

    for (String gate :
        List.of("unit-contract", "localstack", "mcp-contract-leak", "consumer-smoke", "full-reactor")) {
      require(workflow, "aws-ci.sh " + gate);
      require(operations, gate);
    }
    require(workflow, "workflow_call:");
    require(workflow, "workflow_dispatch:");
    require(workflow, "java-version: '25'");
    require(workflow, "cache: maven");
    prohibit(workflow, "actions/cache");
    require(workflow, "localstack/localstack:4.14.0@sha256:3ebc37595918b8accb852f8048fef2aff047d465167edd655528065b07bc364a");
    require(workflow, "aquasecurity/trivy-action@v0.36.0");
    require(workflow, "scanners: vuln,secret");
    require(workflow, "scanners: license");
    require(workflow, "output: target/aws-supply-chain/localstack-licenses.json");
    require(workflow, "severity: UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL");
    prohibit(workflow, "exit-code: '1'");
    require(workflow, "format: cyclonedx");
    require(workflow, "ArtifactLeakCheck");
    require(workflow, "prepare-aws-failure-artifacts.sh");
    require(workflow, "if: ${{ failure() }}");
    require(workflow, "if: ${{ always() }}");
    require(workflow, "environment: authorized-aws-qualification");
    prohibit(workflow, "id-token: write");
    require(
        workflow,
        "does not request credentials, authenticate, or execute live-AWS qualification");
    prohibit(workflow, "AWS_ACCESS_KEY_ID");
    prohibit(workflow, "AWS_SECRET_ACCESS_KEY");
    require(pullRequest, "uses: ./.github/workflows/aws-capability.yml");
    require(matrix, "uses: ./.github/workflows/aws-capability.yml");
    require(dependencyRecord, "sha256:3ebc37595918b8accb852f8048fef2aff047d465167edd655528065b07bc364a");
    require(operations, "authorized-aws-qualification");
    verifyFailureEvidenceRedaction();
    System.out.println("AWS infrastructure and CI contracts passed.");
  }

  private static void verifyFailureEvidenceRedaction() throws Exception {
    Path root = Files.createTempDirectory("aws-artifact-contract-");
    Path source = Files.createDirectories(root.resolve("source"));
    Path destination = root.resolve("sanitized");
    Files.writeString(
        source.resolve("failure.txt"),
        "Authorization: Bearer canary-secret-token credential://operator-profile");
    ArtifactSanitizer.main(new String[] {source.toString(), destination.toString()});
    ArtifactLeakCheck.main(new String[] {destination.toString()});
    String sanitized = Files.readString(destination.resolve("failure.txt"));
    require(sanitized, "[REDACTED]");
    prohibit(sanitized, "canary-secret-token");
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }

  private static void require(String text, String required) {
    if (!text.contains(required)) {
      throw new AssertionError("AWS CI contract is missing: " + required);
    }
  }

  private static void prohibit(String text, String prohibited) {
    if (text.contains(prohibited)) {
      throw new AssertionError("AWS CI contract contains prohibited text: " + prohibited);
    }
  }
}
