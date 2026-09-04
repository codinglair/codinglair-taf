package com.codinglair.taf.mcp.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Loads allowlisted documentation directly from the versioned MCP contract artifact. */
public final class McpContractDocumentation {
  private static final String CATALOG = "META-INF/taf/mcp/catalog/v1/capabilities.json";
  private static final String CAPABILITY_SCHEMA =
      "META-INF/taf/mcp/schema/v1/capability-catalog.schema.json";
  private static final String RESOURCE_SCHEMA = "META-INF/taf/mcp/schema/v1/resource.schema.json";

  private McpContractDocumentation() {}

  public static DocumentationCatalog load(ClassLoader classLoader) {
    try {
      var catalogContent = read(classLoader, CATALOG);
      var catalogVersion =
          new ObjectMapper().readTree(catalogContent).required("catalogVersion").asText();
      return new DocumentationCatalog(
          List.of(
              resource(
                  "mcp-capability-catalog", catalogVersion, "application/json", catalogContent),
              resource(
                  "mcp-capability-schema",
                  "1.0",
                  "application/schema+json",
                  read(classLoader, CAPABILITY_SCHEMA)),
              resource(
                  "mcp-resource-schema",
                  "1.0",
                  "application/schema+json",
                  read(classLoader, RESOURCE_SCHEMA))));
    } catch (IOException | IllegalArgumentException exception) {
      throw new IllegalStateException("versioned MCP documentation is unavailable", exception);
    }
  }

  private static DocumentationResource resource(
      String id, String version, String mediaType, String content) {
    return new DocumentationResource(
        id,
        version,
        "taf://documentation/" + id + "/" + version,
        mediaType,
        content,
        content.getBytes(StandardCharsets.UTF_8).length);
  }

  private static String read(ClassLoader classLoader, String path) throws IOException {
    try (var input = classLoader.getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("missing classpath resource: " + path);
      }
      var bytes = input.readNBytes(DocumentationResource.MAXIMUM_CONTENT_BYTES + 1);
      if (bytes.length > DocumentationResource.MAXIMUM_CONTENT_BYTES) {
        throw new IOException("classpath resource exceeds documentation limit: " + path);
      }
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }
}
