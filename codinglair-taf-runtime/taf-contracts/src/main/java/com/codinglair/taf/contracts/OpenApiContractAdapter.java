package com.codinglair.taf.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Dependency-light OpenAPI 3.x structural validator and Runtime scaffold adapter. */
public final class OpenApiContractAdapter extends StructuredContractAdapter {
  public static final String PROVIDER = "taf-openapi-structural";

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public ContractFormat format() {
    return ContractFormat.OPENAPI;
  }

  @Override
  protected List<ContractDiagnostic> validateRoot(JsonNode root, ContractDocument document) {
    if (!root.isObject() || !root.path("openapi").asText("").startsWith("3.")) {
      return List.of(
          ContractDiagnostic.error(
              "UNSUPPORTED_OPENAPI_VERSION",
              "$.openapi",
              "An OpenAPI 3.x version is required",
              "Set 'openapi' to a supported 3.x version"));
    }
    return requireObjectFields(root, "info", "paths");
  }

  @Override
  protected GeneratedContractAsset generate(ContractScaffoldRequest request, JsonNode root) {
    StringBuilder sb = new StringBuilder();
    var source = sb.append("package ")
            .append(request.packageName())
            .append(";\n\n")
            .append("import com.codinglair.taf.api.rest.RestController;\n\n")
            .append("/** Generated from OpenAPI asset ")
            .append(request.document().assetVersion())
            .append(". */\n")
            .append("public final class ")
            .append(request.className())
            .append(" {\n")
            .append("  public static final String CONTRACT_VERSION = \"")
            .append(request.document().assetVersion())
            .append("\";\n")
            .append("  private final RestController controller;\n\n")
            .append("  public ")
            .append(request.className())
            .append("(RestController controller) {\n")
            .append("    this.controller = java.util.Objects.requireNonNull(controller);\n")
            .append("  }\n\n")
            .append("  public RestController controller() { return controller; }\n")
            .append("}\n")
            .toString();

    return new GeneratedContractAsset(String.format("src/main/java/%s/%s.java",
            request.packageName().replace('.', '/'),
            request.className()),
        request.document().assetVersion(),
        source);
  }
}
