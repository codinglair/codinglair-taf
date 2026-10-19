package com.codinglair.taf.contracts;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import java.util.ArrayList;
import java.util.List;

abstract class StructuredContractAdapter implements ContractAdapter {
  private final ObjectMapper json = JsonMapper.builder().build();
  private final ObjectMapper yaml = YAMLMapper.builder().build();

  @Override
  public final ContractValidationResult validate(ContractDocument document) {
    var mismatch = formatMismatch(document);
    if (mismatch != null) return mismatch;
    try {
      var diagnostics = validateRoot(parse(document.content()), document);
      return new ContractValidationResult(
          diagnostics.isEmpty()
              ? ContractValidationResult.Status.VALID
              : ContractValidationResult.Status.INVALID,
          provider(),
          diagnostics);
    } catch (JacksonException failure) {
      return invalid(
          "MALFORMED_DOCUMENT",
          "$",
          "Contract content is not valid JSON or YAML",
          "Correct the document syntax");
    }
  }

  @Override
  public final ContractScaffoldResult scaffold(ContractScaffoldRequest request) {
    var validation = validate(request.document());
    if (!validation.valid()) {
      return new ContractScaffoldResult(
          validation.status(), validation.provider(), List.of(), validation.diagnostics());
    }
    try {
      return new ContractScaffoldResult(
          ContractValidationResult.Status.VALID,
          provider(),
          List.of(generate(request, parse(request.document().content()))),
          List.of());
    } catch (JacksonException impossibleAfterValidation) {
      throw new IllegalStateException(
          "Validated document could not be parsed", impossibleAfterValidation);
    }
  }

  abstract List<ContractDiagnostic> validateRoot(JsonNode root, ContractDocument document);

  abstract GeneratedContractAsset generate(ContractScaffoldRequest request, JsonNode root);

  final List<ContractDiagnostic> requireObjectFields(JsonNode root, String... fields) {
    var diagnostics = new ArrayList<ContractDiagnostic>();
    for (var field : fields) {
      if (!root.path(field).isObject()) {
        diagnostics.add(
            ContractDiagnostic.error(
                "REQUIRED_OBJECT_MISSING",
                "$.'" + field + "'",
                "Required object '" + field + "' is missing",
                "Add a non-null '" + field + "' object"));
      }
    }
    return List.copyOf(diagnostics);
  }

  private JsonNode parse(String content) throws JacksonException {
    try {
      return json.readTree(content);
    } catch (JacksonException _) {
      return yaml.readTree(content);
    }
  }

  private ContractValidationResult formatMismatch(ContractDocument document) {
    if (document.format() == format()) return null;
    return invalid(
        "FORMAT_MISMATCH",
        "$.format",
        provider() + " cannot process " + document.format(),
        "Use the " + format() + " adapter");
  }

  private ContractValidationResult invalid(
      String code, String location, String message, String correctiveAction) {
    return new ContractValidationResult(
        ContractValidationResult.Status.INVALID,
        provider(),
        List.of(ContractDiagnostic.error(code, location, message, correctiveAction)));
  }
}
