package com.codinglair.taf.api.soap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record SoapResponse(
    int statusCode,
    String envelope,
    Map<String, List<String>> headers,
    List<SoapAttachment> attachments,
    SoapFault fault) {
  public SoapResponse {
    headers = Map.copyOf(headers);
    attachments = List.copyOf(attachments);
  }

  public Optional<SoapFault> soapFault() {
    return Optional.ofNullable(fault);
  }
}
