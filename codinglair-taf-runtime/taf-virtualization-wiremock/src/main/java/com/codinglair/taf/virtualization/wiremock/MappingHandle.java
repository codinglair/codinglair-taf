package com.codinglair.taf.virtualization.wiremock;

import java.net.URI;
import java.util.UUID;

public record MappingHandle(UUID id, URI endpoint, FaultClassification classification) {}
