package com.codinglair.taf.api.soap;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Hardened namespace-aware XML, XPath, XSD, and deterministic comparison utilities. */
public final class SoapXml {
  private SoapXml() {}

  public static Document parse(String xml) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    } catch (Exception failure) {
      throw new IllegalArgumentException(
          "XML is malformed or uses prohibited external content", failure);
    }
  }

  public static String xpath(String xml, String expression, Map<String, String> namespaces) {
    try {
      var xpath = XPathFactory.newInstance().newXPath();
      xpath.setNamespaceContext(new MapNamespaceContext(namespaces));
      return (String) xpath.evaluate(expression, parse(xml), XPathConstants.STRING);
    } catch (Exception failure) {
      throw new IllegalArgumentException("XPath evaluation failed", failure);
    }
  }

  public static void validateXsd(String xml, String xsd) {
    try {
      var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      var schema =
          factory.newSchema(new javax.xml.transform.stream.StreamSource(new StringReader(xsd)));
      schema
          .newValidator()
          .validate(new javax.xml.transform.stream.StreamSource(new StringReader(xml)));
    } catch (SAXException failure) {
      throw new AssertionError("XML does not satisfy XSD: " + failure.getMessage(), failure);
    } catch (Exception failure) {
      throw new IllegalArgumentException("XSD validation could not run", failure);
    }
  }

  public static void assertEquivalent(
      String expected, String actual, List<String> ignoredXpaths, Map<String, String> namespaces) {
    Document left = parse(expected);
    Document right = parse(actual);
    ignoredXpaths.forEach(
        path -> {
          remove(left, path, namespaces);
          remove(right, path, namespaces);
        });
    String leftCanonical = canonical(left.getDocumentElement());
    String rightCanonical = canonical(right.getDocumentElement());
    if (!leftCanonical.equals(rightCanonical))
      throw new AssertionError(
          "XML differs\nexpected=" + leftCanonical + "\nactual=" + rightCanonical);
  }

  static String serialize(Document document) {
    try {
      var transformer = TransformerFactory.newInstance();
      transformer.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      transformer.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      var instance = transformer.newTransformer();
      instance.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      var writer = new StringWriter();
      instance.transform(new DOMSource(document), new StreamResult(writer));
      return writer.toString();
    } catch (Exception failure) {
      throw new IllegalStateException("XML serialization failed", failure);
    }
  }

  static String sanitizeSecurity(String xml) {
    Document document = parse(xml);
    for (String localName :
        List.of("Username", "Password", "BinarySecurityToken", "SignatureValue", "CipherValue")) {
      NodeList nodes = document.getElementsByTagNameNS("*", localName);
      for (int index = 0; index < nodes.getLength(); index++) {
        nodes.item(index).setTextContent("[REDACTED]");
      }
    }
    return serialize(document);
  }

  static SoapFault fault(Document document, SoapVersion version) {
    NodeList faults = document.getElementsByTagNameNS(version.envelopeNamespace(), "Fault");
    if (faults.getLength() == 0) return null;
    Element fault = (Element) faults.item(0);
    if (version == SoapVersion.SOAP_11)
      return new SoapFault(
          text(fault, "faultcode"), text(fault, "faultstring"), text(fault, "detail"));
    return new SoapFault(
        descendant(fault, version.envelopeNamespace(), "Value"),
        descendant(fault, version.envelopeNamespace(), "Text"),
        descendant(fault, version.envelopeNamespace(), "Detail"));
  }

  private static void remove(Document document, String expression, Map<String, String> namespaces) {
    try {
      var xpath = XPathFactory.newInstance().newXPath();
      xpath.setNamespaceContext(new MapNamespaceContext(namespaces));
      NodeList nodes = (NodeList) xpath.evaluate(expression, document, XPathConstants.NODESET);
      List<Node> snapshot = new ArrayList<>();
      for (int index = 0; index < nodes.getLength(); index++) snapshot.add(nodes.item(index));
      snapshot.forEach(node -> node.getParentNode().removeChild(node));
    } catch (Exception failure) {
      throw new IllegalArgumentException("Ignored XPath is invalid: " + expression, failure);
    }
  }

  private static String canonical(Node node) {
    if (node.getNodeType() == Node.TEXT_NODE) return node.getTextContent().strip();
    if (node.getNodeType() != Node.ELEMENT_NODE) return "";
    Element element = (Element) node;
    StringBuilder result =
        new StringBuilder()
            .append('{')
            .append(element.getNamespaceURI())
            .append('}')
            .append(element.getLocalName());
    var attributes = new LinkedHashMap<String, String>();
    for (int index = 0; index < element.getAttributes().getLength(); index++) {
      Node attribute = element.getAttributes().item(index);
      if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI()))
        attributes.put(
            "{" + attribute.getNamespaceURI() + "}" + attribute.getLocalName(),
            attribute.getNodeValue());
    }
    attributes.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry ->
                result.append('|').append(entry.getKey()).append('=').append(entry.getValue()));
    result.append('>');
    for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling())
      result.append(canonical(child));
    return result
        .append("</{")
        .append(element.getNamespaceURI())
        .append('}')
        .append(element.getLocalName())
        .append('>')
        .toString();
  }

  private static String text(Element element, String name) {
    NodeList nodes = element.getElementsByTagName(name);
    return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().strip();
  }

  private static String descendant(Element element, String namespace, String name) {
    NodeList nodes = element.getElementsByTagNameNS(namespace, name);
    return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().strip();
  }

  private record MapNamespaceContext(Map<String, String> values) implements NamespaceContext {
    MapNamespaceContext {
      values = values == null ? Map.of() : Map.copyOf(values);
    }

    public String getNamespaceURI(String prefix) {
      return values.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
    }

    public String getPrefix(String uri) {
      return values.entrySet().stream()
          .filter(entry -> entry.getValue().equals(uri))
          .map(Map.Entry::getKey)
          .findFirst()
          .orElse(null);
    }

    public Iterator<String> getPrefixes(String uri) {
      return values.entrySet().stream()
          .filter(entry -> entry.getValue().equals(uri))
          .map(Map.Entry::getKey)
          .iterator();
    }
  }
}
