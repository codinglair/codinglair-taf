package com.codinglair.taf.contracts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.ArrayList;
import java.util.List;

abstract class StructuredContractAdapter implements ContractAdapter {
  private final ObjectMapper json = new ObjectMapper();
  private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

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
    } catch (JsonProcessingException failure) {
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
    } catch (JsonProcessingException impossibleAfterValidation) {
      throw new IllegalStateException(
          "Validated document could not be parsed", impossibleAfterValidation);
    }
  }

  protected abstract List<ContractDiagnostic> validateRoot(
      JsonNode root, ContractDocument document);

  protected abstract GeneratedContractAsset generate(
      ContractScaffoldRequest request, JsonNode root);

  protected final List<ContractDiagnostic> requireObjectFields(JsonNode root, String... fields) {
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

  private JsonNode parse(String content) throws JsonProcessingException {
    try {
      return json.readTree(content);
    } catch (JsonProcessingException ignored) {
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
