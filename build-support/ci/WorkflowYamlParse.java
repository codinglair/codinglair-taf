import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Path;

/**
 * CI entry point that fails closed when an executable workflow is not a YAML mapping.
 *
 * <p>This remains in {@code build-support/ci} beside the structural workflow contract and uses
 * the repository-managed Jackson YAML dependency assembled by the change-impact job.
 */
final class WorkflowYamlParse {
  private WorkflowYamlParse() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Expected one workflow path");
    }
    var root = new ObjectMapper(new YAMLFactory()).readTree(Path.of(args[0]).toFile());
    if (root == null || !root.isObject()) {
      throw new IllegalStateException("Workflow YAML root must be a mapping");
    }
    System.out.println("GitHub workflow YAML parsed successfully: " + args[0]);
  }
}
