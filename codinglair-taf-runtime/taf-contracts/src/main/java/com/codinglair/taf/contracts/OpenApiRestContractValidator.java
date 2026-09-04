package com.codinglair.taf.contracts;

import com.codinglair.taf.api.rest.RestContractValidator;
import com.codinglair.taf.api.rest.RestRequest;
import com.codinglair.taf.api.rest.RestResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Validates completed REST exchanges against paths, operations, and response codes in OpenAPI. */
public final class OpenApiRestContractValidator implements RestContractValidator {
  private final JsonNode paths;

  public OpenApiRestContractValidator(ContractDocument document) {
    Objects.requireNonNull(document, "document must not be null");
    var validation = new OpenApiContractAdapter().validate(document);
    if (!validation.valid()) {
      throw new IllegalArgumentException(
          "OpenAPI document is invalid: " + validation.diagnostics().getFirst().code());
    }
    paths = parse(document.content()).path("paths");
  }

  @Override
  public void validate(RestRequest request, RestResponse response) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(response, "response must not be null");
    var pathEntry = findPath(request.path());
    if (pathEntry == null) {
      throw new ContractViolationException("OpenAPI path is not declared: " + request.path());
    }
    var operation = pathEntry.path(request.method().name().toLowerCase(Locale.ROOT));
    if (!operation.isObject()) {
      throw new ContractViolationException(
          "OpenAPI operation is not declared: " + request.method() + " " + request.path());
    }
    var responses = operation.path("responses");
    var status = Integer.toString(response.statusCode());
    if (!responses.has(status) && !responses.has("default")) {
      throw new ContractViolationException(String.format(
          "OpenAPI response is not declared: %s %s -> %s",
              request.method(), request.path(), status));
    }
  }

  private JsonNode findPath(String requestPath) {
    var fields = paths.fields();
    while (fields.hasNext()) {
      var candidate = fields.next();
      var expression = pathExpression(candidate.getKey());
      if (requestPath.matches(expression)) return candidate.getValue();
    }
    return null;
  }

  private static String pathExpression(String template) {
    var parameter = Pattern.compile("\\{[^/{}]+}").matcher(template);
    var expression = new StringBuilder("^");
    var start = 0;
    while (parameter.find()) {
      expression
          .append(Pattern.quote(template.substring(start, parameter.start())))
          .append("[^/]+");
      start = parameter.end();
    }
    return expression.append(Pattern.quote(template.substring(start))).append('$').toString();
  }

  private static JsonNode parse(String content) {
    try {
      return new ObjectMapper().readTree(content);
    } catch (JsonProcessingException ignored) {
      try {
        return new ObjectMapper(new YAMLFactory()).readTree(content);
      } catch (JsonProcessingException impossibleAfterValidation) {
        throw new IllegalStateException(
            "Validated OpenAPI document could not be parsed", impossibleAfterValidation);
      }
    }
  }
}
