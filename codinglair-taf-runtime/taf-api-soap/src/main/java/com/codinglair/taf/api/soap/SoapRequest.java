package com.codinglair.taf.api.soap;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SoapRequest(
    SoapVersion version,
    String action,
    String envelope,
    Map<String, String> headers,
    List<SoapAttachment> attachments,
    SoapMessageSecurity security) {
  public SoapRequest {
    Objects.requireNonNull(version, "version");
    if (envelope == null || envelope.isBlank())
      throw new IllegalArgumentException("SOAP envelope must not be blank");
    headers = headers == null ? Map.of() : Map.copyOf(headers);
    attachments = attachments == null ? List.of() : List.copyOf(attachments);
    security = security == null ? SoapMessageSecurity.NONE : security;
  }

  public static SoapRequest of(SoapVersion version, String action, String envelope) {
    return new SoapRequest(
        version, action, envelope, Map.of(), List.of(), SoapMessageSecurity.NONE);
  }
}
