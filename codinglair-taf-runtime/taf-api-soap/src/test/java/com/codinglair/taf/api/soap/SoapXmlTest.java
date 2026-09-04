package com.codinglair.taf.api.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SOAP XML utilities")
class SoapXmlTest {
  @Nested
  @DisplayName("Namespace-aware behavior")
  class NamespaceBehavior {
    @Test
    @DisplayName(
        "compares equivalent namespace documents regardless of prefixes and attribute order")
    void comparesNamespaces() {
      SoapXml.assertEquivalent(
          "<a:r xmlns:a='urn:x' z='2' a:y='1'><a:v>ok</a:v></a:r>",
          "<b:r xmlns:b='urn:x' b:y='1' z='2'><b:v>ok</b:v></b:r>",
          List.of(),
          Map.of());
    }

    @Test
    @DisplayName("ignores configured XPath fields deterministically")
    void ignoresXpaths() {
      SoapXml.assertEquivalent(
          "<r xmlns='urn:x'><id>1</id><v>ok</v></r>",
          "<r xmlns='urn:x'><id>2</id><v>ok</v></r>",
          List.of("/x:r/x:id"),
          Map.of("x", "urn:x"));
    }

    @Test
    @DisplayName("evaluates XPath using explicit namespace mappings")
    void xpath() {
      assertThat(
              SoapXml.xpath(
                  "<r xmlns='urn:x'><v>ok</v></r>", "string(/x:r/x:v)", Map.of("x", "urn:x")))
          .isEqualTo("ok");
    }
  }

  @Nested
  @DisplayName("Security and validation")
  class ValidationBehavior {
    @Test
    @DisplayName("rejects XML external entities")
    void rejectsXxe() {
      assertThrows(
          IllegalArgumentException.class,
          () -> SoapXml.parse("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>"));
    }

    @Test
    @DisplayName("validates XML against XSD")
    void validatesXsd() {
      SoapXml.validateXsd(
          "<v xmlns='urn:x'>ok</v>",
          "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:x' xmlns='urn:x' elementFormDefault='qualified'><xs:element name='v' type='xs:string'/></xs:schema>");
    }

    @Test
    @DisplayName("reports invalid XML as an assertion failure")
    void invalidXsd() {
      assertThrows(
          AssertionError.class,
          () ->
              SoapXml.validateXsd(
                  "<bad/>",
                  "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'><xs:element name='good'/></xs:schema>"));
    }
  }
}
