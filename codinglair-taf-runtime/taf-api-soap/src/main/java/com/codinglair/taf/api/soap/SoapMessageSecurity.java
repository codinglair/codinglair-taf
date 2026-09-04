package com.codinglair.taf.api.soap;

import org.w3c.dom.Document;

/** Secret-safe outbound WS-Security boundary. Implementations must not retain resolved values. */
@FunctionalInterface
public interface SoapMessageSecurity {
  SoapMessageSecurity NONE = document -> {};

  void secure(Document document);
}
