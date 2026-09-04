package com.codinglair.taf.runtime.file;

import com.codinglair.taf.runtime.file.FileSandbox.CheckedFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

final class StructuredFileComparator {
  private final FileSandbox sandbox;
  private final ObjectMapper json = new ObjectMapper();

  StructuredFileComparator(FileSandbox sandbox) {
    this.sandbox = sandbox;
  }

  FileValidationResult compare(FileComparisonRequest request) {
    CheckedFile expected = sandbox.check(request.expected());
    CheckedFile actual = sandbox.check(request.actual());
    FileComparisonOptions options = request.options();
    try {
      List<String> differences =
          switch (request.format()) {
            case JSON -> compareValues(json(expected), json(actual), options);
            case XML -> compareValues(xml(expected), xml(actual), options);
            case CSV ->
                compareValues(
                    delimited(expected, options.charset(), options.csvDelimiter()),
                    delimited(actual, options.charset(), options.csvDelimiter()),
                    options);
            case FIXED_WIDTH ->
                compareValues(fixed(expected, options), fixed(actual, options), options);
            case EXCEL -> compareValues(excel(expected), excel(actual), options);
            case PDF -> comparePdf(expected, actual);
          };
      if (differences.size() > options.maximumDifferences())
        differences = differences.subList(0, options.maximumDifferences());
      String expectedHash = digest(expected);
      String actualHash = digest(actual);
      FileEvidenceSummary evidence =
          new FileEvidenceSummary(
              request.format().name(),
              differences.isEmpty(),
              expected.size(),
              actual.size(),
              expectedHash,
              actualHash,
              options.ignoredFields(),
              options.numericTolerance().toPlainString(),
              differences);
      return new FileValidationResult(differences.isEmpty(), differences, evidence);
    } catch (FileValidationException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new FileValidationException(
          FileValidationException.Kind.INVALID_FORMAT,
          "parse " + request.format(),
          "verify the file format and comparison options",
          null);
    }
  }

  private SortedMap<String, String> json(CheckedFile file) throws IOException {
    try (InputStream input = sandbox.open(file)) {
      JsonNode root = json.readTree(input);
      if (root == null) throw new IOException("empty JSON");
      SortedMap<String, String> values = new TreeMap<>();
      flattenJson(root, "$", values);
      return values;
    }
  }

  private static void flattenJson(JsonNode node, String path, Map<String, String> values) {
    if (node.isObject()) {
      var names = new ArrayList<String>();
      node.fieldNames().forEachRemaining(names::add);
      names.stream()
          .sorted()
          .forEach(name -> flattenJson(node.get(name), path + "." + name, values));
    } else if (node.isArray()) {
      for (int index = 0; index < node.size(); index++)
        flattenJson(node.get(index), path + "[" + index + "]", values);
    } else
      values.put(
          path,
          node.isNumber()
              ? node.decimalValue().stripTrailingZeros().toPlainString()
              : node.asText());
  }

  private SortedMap<String, String> xml(CheckedFile file) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    try (InputStream input = sandbox.open(file)) {
      Document document = factory.newDocumentBuilder().parse(input);
      SortedMap<String, String> values = new TreeMap<>();
      flattenXml(document.getDocumentElement(), "", values);
      return values;
    }
  }

  private static void flattenXml(Element element, String parent, Map<String, String> values) {
    String name = element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    String path = parent + "/" + name;
    NamedNodeMap attributes = element.getAttributes();
    for (int index = 0; index < attributes.getLength(); index++) {
      Node attribute = attributes.item(index);
      if (!attribute.getNodeName().startsWith("xmlns"))
        values.put(path + "/@" + attribute.getNodeName(), attribute.getNodeValue());
    }
    Map<String, Integer> occurrences = new HashMap<>();
    boolean childElement = false;
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling())
      if (child instanceof Element nested) {
        childElement = true;
        String childName =
            nested.getLocalName() == null ? nested.getTagName() : nested.getLocalName();
        int occurrence = occurrences.merge(childName, 1, Integer::sum) - 1;
        flattenXml(nested, path + "[" + occurrence + "]", values);
      }
    if (!childElement) values.put(path, element.getTextContent().strip());
  }

  private SortedMap<String, String> delimited(CheckedFile file, Charset charset, char delimiter)
      throws IOException {
    SortedMap<String, String> values = new TreeMap<>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(sandbox.open(file), charset))) {
      String line;
      int row = 0;
      while ((line = reader.readLine()) != null) {
        List<String> cells = csvLine(line, delimiter);
        for (int column = 0; column < cells.size(); column++)
          values.put("row[" + row + "].column[" + column + "]", cells.get(column));
        row++;
      }
    }
    return values;
  }

  private static List<String> csvLine(String line, char delimiter) throws IOException {
    List<String> cells = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int index = 0; index < line.length(); index++) {
      char current = line.charAt(index);
      if (current == '"') {
        if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
          cell.append('"');
          index++;
        } else quoted = !quoted;
      } else if (current == delimiter && !quoted) {
        cells.add(cell.toString());
        cell.setLength(0);
      } else cell.append(current);
    }
    if (quoted) throw new IOException("unterminated CSV quote");
    cells.add(cell.toString());
    return cells;
  }

  private SortedMap<String, String> fixed(CheckedFile file, FileComparisonOptions options)
      throws IOException {
    if (options.fixedWidths().isEmpty()) throw new IOException("fixed widths required");
    SortedMap<String, String> values = new TreeMap<>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(sandbox.open(file), options.charset()))) {
      String line;
      int row = 0;
      while ((line = reader.readLine()) != null) {
        int offset = 0;
        for (int column = 0; column < options.fixedWidths().size(); column++) {
          int end = offset + options.fixedWidths().get(column);
          if (end > line.length()) throw new IOException("short fixed-width row");
          values.put("row[" + row + "].column[" + column + "]", line.substring(offset, end));
          offset = end;
        }
        if (offset != line.length()) throw new IOException("long fixed-width row");
        row++;
      }
    }
    return values;
  }

  private SortedMap<String, String> excel(CheckedFile file) throws Exception {
    SortedMap<String, byte[]> entries = new TreeMap<>();
    long expandedBytes = 0;
    try (ZipInputStream zip = new ZipInputStream(sandbox.open(file))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        String name = entry.getName();
        if (name.equals("xl/sharedStrings.xml") || name.startsWith("xl/worksheets/sheet")) {
          byte[] content = readBoundedEntry(zip, sandbox.maximumSize() - expandedBytes);
          expandedBytes += content.length;
          entries.put(name, content);
        }
      }
    }
    if (entries.keySet().stream().noneMatch(name -> name.startsWith("xl/worksheets/sheet")))
      throw new IOException("workbook has no worksheets");
    List<String> shared =
        entries.containsKey("xl/sharedStrings.xml")
            ? sharedStrings(entries.get("xl/sharedStrings.xml"))
            : List.of();
    SortedMap<String, String> values = new TreeMap<>();
    for (var entry : entries.entrySet())
      if (entry.getKey().startsWith("xl/worksheets/sheet"))
        worksheet(entry.getKey(), entry.getValue(), shared, values);
    return values;
  }

  private static byte[] readBoundedEntry(InputStream input, long remaining) throws IOException {
    if (remaining < 1) throw new IOException("expanded workbook exceeds limit");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    long count = 0;
    int read;
    while ((read = input.read(buffer)) >= 0) {
      count += read;
      if (count > remaining) throw new IOException("expanded workbook exceeds limit");
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }

  private static Document parseXmlBytes(byte[] bytes) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(bytes)));
  }

  private static List<String> sharedStrings(byte[] bytes) throws Exception {
    NodeList nodes = parseXmlBytes(bytes).getElementsByTagNameNS("*", "si");
    List<String> result = new ArrayList<>();
    for (int index = 0; index < nodes.getLength(); index++)
      result.add(nodes.item(index).getTextContent());
    return result;
  }

  private static void worksheet(
      String sheet, byte[] bytes, List<String> shared, Map<String, String> values)
      throws Exception {
    NodeList cells = parseXmlBytes(bytes).getElementsByTagNameNS("*", "c");
    for (int index = 0; index < cells.getLength(); index++) {
      Element cell = (Element) cells.item(index);
      NodeList content = cell.getElementsByTagNameNS("*", "v");
      String value = content.getLength() == 0 ? "" : content.item(0).getTextContent();
      if ("s".equals(cell.getAttribute("t"))) value = shared.get(Integer.parseInt(value));
      values.put(sheet + "/" + cell.getAttribute("r"), value);
    }
  }

  private List<String> comparePdf(CheckedFile expected, CheckedFile actual) throws Exception {
    if (!pdfHeader(expected) || !pdfHeader(actual)) throw new IOException("invalid PDF header");
    return digest(expected).equals(digest(actual)) ? List.of() : List.of("binary digest differs");
  }

  private boolean pdfHeader(CheckedFile file) throws IOException {
    try (InputStream input = sandbox.open(file)) {
      return Arrays.equals(
          input.readNBytes(5), "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
  }

  private String digest(CheckedFile file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = sandbox.open(file)) {
      input.transferTo(
          new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest));
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static List<String> compareValues(
      SortedMap<String, String> expected,
      SortedMap<String, String> actual,
      FileComparisonOptions options) {
    SortedSet<String> keys = new TreeSet<>();
    keys.addAll(expected.keySet());
    keys.addAll(actual.keySet());
    List<String> differences = new ArrayList<>();
    for (String key : keys) {
      if (options.ignoredFields().contains(key)) continue;
      String left = expected.get(key);
      String right = actual.get(key);
      if (!equivalent(left, right, options.numericTolerance())) differences.add(key + " differs");
    }
    return differences;
  }

  private static boolean equivalent(String left, String right, BigDecimal tolerance) {
    if (Objects.equals(left, right)) return true;
    if (left == null || right == null || tolerance.signum() == 0) return false;
    try {
      return new BigDecimal(left).subtract(new BigDecimal(right)).abs().compareTo(tolerance) <= 0;
    } catch (NumberFormatException ignored) {
      return false;
    }
  }
}
