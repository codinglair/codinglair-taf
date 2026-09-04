package com.codinglair.taf.api.soap;

public enum SoapVersion {
  SOAP_11("text/xml", "http://schemas.xmlsoap.org/soap/envelope/"),
  SOAP_12("application/soap+xml", "http://www.w3.org/2003/05/soap-envelope");

  private final String contentType;
  private final String envelopeNamespace;

  SoapVersion(String contentType, String envelopeNamespace) {
    this.contentType = contentType;
    this.envelopeNamespace = envelopeNamespace;
  }

  public String contentType() {
    return contentType;
  }

  public String envelopeNamespace() {
    return envelopeNamespace;
  }
}
