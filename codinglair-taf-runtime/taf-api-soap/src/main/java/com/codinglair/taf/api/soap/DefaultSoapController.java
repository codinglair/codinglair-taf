package com.codinglair.taf.api.soap;

import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

final class DefaultSoapController implements SoapController {
  private final ControllerIdentity identity;
  private final SoapControllerSettings settings;
  private final AtomicReference<ControllerState> state = new AtomicReference<>(ControllerState.NEW);
  private ControllerContext context;
  private HttpClient client;

  DefaultSoapController(String name, SoapControllerSettings settings) {
    identity = new ControllerIdentity(SoapController.class, name);
    this.settings = Objects.requireNonNull(settings);
  }

  public ControllerIdentity identity() {
    return identity;
  }

  public ControllerState state() {
    return state.get();
  }

  public void initialize(ControllerContext value) {
    if (!state.compareAndSet(ControllerState.NEW, ControllerState.INITIALIZING))
      throw new IllegalStateException("Controller cannot initialize from " + state.get());
    try {
      settings.validate("taf.api.soap.controllers." + identity.name());
      context = Objects.requireNonNull(value);
      client = HttpClient.newBuilder().connectTimeout(settings.getTimeout()).build();
      state.set(ControllerState.READY);
    } catch (RuntimeException failure) {
      state.set(ControllerState.FAILED);
      throw new SoapControllerException(
          "initialize", "correct the named SOAP controller configuration", failure);
    }
  }

  public HealthResult health() {
    return state.get() == ControllerState.READY
        ? new HealthResult(
            HealthResult.Status.HEALTHY,
            "SOAP controller is ready",
            Map.of("endpoint", settings.getEndpoint().toString()))
        : HealthResult.unknown("SOAP controller is not ready: " + state.get());
  }

  public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
    return Stream.empty();
  }

  public void close() {
    client = null;
    context = null;
    state.set(ControllerState.CLOSED);
  }

  @ControllerAction("Exchange SOAP message")
  public SoapResponse exchange(SoapRequest request) {
    ensureReady();
    Objects.requireNonNull(request);
    try {
      var document = SoapXml.parse(request.envelope());
      if (!request
          .version()
          .envelopeNamespace()
          .equals(document.getDocumentElement().getNamespaceURI()))
        throw new IllegalArgumentException(
            "Envelope namespace does not match " + request.version());
      request.security().secure(document);
      String secured = SoapXml.serialize(document);
      String boundary = "taf-" + UUID.randomUUID();
      byte[] body =
          request.attachments().isEmpty()
              ? secured.getBytes(StandardCharsets.UTF_8)
              : multipart(secured, request, boundary);
      String contentType =
          request.attachments().isEmpty()
              ? request.version().contentType() + "; charset=UTF-8"
              : "multipart/related; type=\""
                  + request.version().contentType()
                  + "\"; boundary=\""
                  + boundary
                  + "\"; start=\"<root@taf>\"";
      var builder =
          HttpRequest.newBuilder(settings.getEndpoint())
              .timeout(settings.getTimeout())
              .header("Content-Type", contentType)
              .POST(HttpRequest.BodyPublishers.ofByteArray(body));
      if (request.action() != null && request.version() == SoapVersion.SOAP_11)
        builder.header("SOAPAction", '"' + request.action() + '"');
      request.headers().forEach(builder::header);
      HttpResponse<byte[]> nativeResponse =
          client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
      if (nativeResponse.body().length > settings.getMaxResponseBytes())
        throw new IllegalStateException("SOAP response exceeds configured maximum");
      ParsedResponse parsed = parseResponse(nativeResponse);
      SoapResponse response =
          new SoapResponse(
              nativeResponse.statusCode(),
              parsed.envelope,
              nativeResponse.headers().map(),
              parsed.attachments,
              SoapXml.fault(SoapXml.parse(parsed.envelope), request.version()));
      capture(SoapXml.sanitizeSecurity(secured), response);
      return response;
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new SoapControllerException(
          "exchange", "preserve cancellation and retry only when appropriate", failure);
    } catch (SoapControllerException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new SoapControllerException(
          "exchange",
          "verify endpoint, envelope, security references, and network availability",
          failure);
    }
  }

  private void capture(String request, SoapResponse response) {
    context
        .artifacts()
        .addArtifact(
            "soap-request-" + context.artifacts().nextSequenceNumber() + ".xml",
            "soap-request",
            request,
            "application/xml",
            null);
    context
        .artifacts()
        .addArtifact(
            "soap-response-" + context.artifacts().nextSequenceNumber() + ".xml",
            "soap-response",
            response.envelope(),
            "application/xml",
            null);
    response
        .attachments()
        .forEach(
            part ->
                context
                    .artifacts()
                    .addArtifact(
                        "soap-attachment-" + context.artifacts().nextSequenceNumber() + ".txt",
                        "soap-attachment",
                        "contentId="
                            + part.contentId()
                            + "\ncontentType="
                            + part.contentType()
                            + "\nsize="
                            + part.content().length,
                        "text/plain",
                        null));
  }

  private static byte[] multipart(String envelope, SoapRequest request, String boundary) {
    var output = new java.io.ByteArrayOutputStream();
    try {
      write(
          output,
          "--"
              + boundary
              + "\r\nContent-Type: "
              + request.version().contentType()
              + "; charset=UTF-8\r\nContent-ID: <root@taf>\r\n\r\n"
              + envelope
              + "\r\n");
      for (SoapAttachment part : request.attachments()) {
        write(
            output,
            "--"
                + boundary
                + "\r\nContent-Type: "
                + part.contentType()
                + "\r\nContent-ID: <"
                + part.contentId()
                + ">\r\n\r\n");
        output.write(part.content());
        write(output, "\r\n");
      }
      write(output, "--" + boundary + "--\r\n");
      return output.toByteArray();
    } catch (java.io.IOException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static ParsedResponse parseResponse(HttpResponse<byte[]> response) {
    String contentType = response.headers().firstValue("Content-Type").orElse("application/xml");
    if (!contentType.toLowerCase(Locale.ROOT).startsWith("multipart/"))
      return new ParsedResponse(new String(response.body(), StandardCharsets.UTF_8), List.of());
    String boundary = parameter(contentType, "boundary");
    if (boundary == null)
      throw new IllegalArgumentException("Multipart SOAP response has no boundary");
    String raw = new String(response.body(), StandardCharsets.ISO_8859_1);
    String[] parts = raw.split("--" + java.util.regex.Pattern.quote(boundary));
    String envelope = null;
    List<SoapAttachment> attachments = new ArrayList<>();
    for (String part : parts) {
      int split = part.indexOf("\r\n\r\n");
      if (split < 0) continue;
      String headers = part.substring(0, split);
      byte[] content =
          part.substring(split + 4).replaceFirst("\r\n$", "").getBytes(StandardCharsets.ISO_8859_1);
      String type = header(headers, "Content-Type");
      String id = header(headers, "Content-ID");
      if (envelope == null && type != null && (type.contains("xml") || type.contains("soap")))
        envelope = new String(content, StandardCharsets.UTF_8);
      else
        attachments.add(
            new SoapAttachment(
                id == null ? "unknown" : id.replace("<", "").replace(">", ""),
                type == null ? "application/octet-stream" : type,
                content));
    }
    if (envelope == null)
      throw new IllegalArgumentException("Multipart SOAP response has no envelope part");
    return new ParsedResponse(envelope, attachments);
  }

  private static String parameter(String value, String name) {
    for (String part : value.split(";")) {
      String[] pair = part.strip().split("=", 2);
      if (pair.length == 2 && pair[0].equalsIgnoreCase(name))
        return pair[1].strip().replace("\"", "");
    }
    return null;
  }

  private static String header(String headers, String name) {
    return headers
        .lines()
        .map(String::strip)
        .filter(line -> line.regionMatches(true, 0, name + ":", 0, name.length() + 1))
        .map(line -> line.substring(name.length() + 1).strip())
        .findFirst()
        .orElse(null);
  }

  private static void write(java.io.ByteArrayOutputStream output, String value)
      throws java.io.IOException {
    output.write(value.getBytes(StandardCharsets.UTF_8));
  }

  private void ensureReady() {
    if (state.get() != ControllerState.READY)
      throw new IllegalStateException("SOAP controller is not READY: " + state.get());
  }

  private record ParsedResponse(String envelope, List<SoapAttachment> attachments) {}
}
