package com.codinglair.taf.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Dependency-light AsyncAPI 2.x/3.x structural validator and Runtime scaffold adapter. */
public final class AsyncApiContractAdapter extends StructuredContractAdapter {
  public static final String PROVIDER = "taf-asyncapi-structural";

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public ContractFormat format() {
    return ContractFormat.ASYNCAPI;
  }

  @Override
  protected List<ContractDiagnostic> validateRoot(JsonNode root, ContractDocument document) {
    var version = root.path("asyncapi").asText("");
    if (!root.isObject() || !(version.startsWith("2.") || version.startsWith("3."))) {
      return List.of(
          ContractDiagnostic.error(
              "UNSUPPORTED_ASYNCAPI_VERSION",
              "$.asyncapi",
              "An AsyncAPI 2.x or 3.x version is required",
              "Set 'asyncapi' to a supported 2.x or 3.x version"));
    }
    return requireObjectFields(root, "info", "channels");
  }

  @Override
  protected GeneratedContractAsset generate(ContractScaffoldRequest request, JsonNode root) {
    StringBuilder sb = new StringBuilder();
    var source = sb.append("package ")
            .append(request.packageName())
            .append(";\n\n")
            .append("import com.codinglair.taf.messaging.MessagingController;\n\n")
            .append("/** Generated from AsyncAPI asset ")
            .append(request.document().assetVersion())
            .append(". */\n")
            .append("public final class ")
            .append(request.className())
            .append(" {\n")
            .append("  public static final String CONTRACT_VERSION = \"")
            .append(request.document().assetVersion())
            .append("\";\n")
            .append("  private final MessagingController controller;\n\n")
            .append("  public ")
            .append(request.className())
            .append("(MessagingController controller) {\n")
            .append("    this.controller = java.util.Objects.requireNonNull(controller);\n")
            .append("  }\n\n")
            .append("  public MessagingController controller() { return controller; }\n")
            .append("}\n")
            .toString();

    return new GeneratedContractAsset(String.format("src/main/java/%s/%s.java", 
              request.packageName().replace('.', '/'), 
              request.className()),
            request.document().assetVersion(), 
            source);
  }
}
