package com.codinglair.taf.api.soap;

import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;
import java.util.Objects;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** WS-Security UsernameToken using a password reference, never a stored password. */
public final class UsernameTokenSecurity implements SoapMessageSecurity {
  private static final String WSSE =
      "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
  private final SecretManager secrets;
  private final String usernameReference;
  private final String passwordReference;
  private final SecretRequestContext requestContext;

  public UsernameTokenSecurity(
      SecretManager secrets,
      String usernameReference,
      String passwordReference,
      SecretRequestContext requestContext) {
    this.secrets = Objects.requireNonNull(secrets);
    this.usernameReference = requireReference(usernameReference);
    this.passwordReference = requireReference(passwordReference);
    this.requestContext = Objects.requireNonNull(requestContext);
  }

  @Override
  public void secure(Document document) {
    Element envelope = document.getDocumentElement();
    String soap = envelope.getNamespaceURI();
    Element header = (Element) document.getElementsByTagNameNS(soap, "Header").item(0);
    if (header == null) {
      header = document.createElementNS(soap, "soap:Header");
      envelope.insertBefore(header, envelope.getFirstChild());
    }
    Element security = document.createElementNS(WSSE, "wsse:Security");
    Element token = document.createElementNS(WSSE, "wsse:UsernameToken");
    try (var username = secrets.resolve(usernameReference, requestContext);
        var password = secrets.resolve(passwordReference, requestContext)) {
      Element usernameNode = document.createElementNS(WSSE, "wsse:Username");
      usernameNode.setTextContent(username.useAsString());
      Element passwordNode = document.createElementNS(WSSE, "wsse:Password");
      passwordNode.setTextContent(password.useAsString());
      token.appendChild(usernameNode);
      token.appendChild(passwordNode);
      security.appendChild(token);
      header.appendChild(security);
    }
  }

  @Override
  public String toString() {
    return "UsernameTokenSecurity[references=REDACTED]";
  }

  private static String requireReference(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Secret reference must not be blank");
    return value;
  }
}
