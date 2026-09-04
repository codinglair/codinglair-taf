package com.codinglair.taf.mcp.resources;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Immutable catalog of explicitly approved, versioned documentation resources. */
public final class DocumentationCatalog {
  private final List<DocumentationResource> documents;

  public DocumentationCatalog(Collection<DocumentationResource> documents) {
    var ids = new HashSet<String>();
    this.documents =
        documents.stream()
            .peek(
                document -> {
                  if (!ids.add(document.id() + "@" + document.version())) {
                    throw new IllegalArgumentException("duplicate documentation version");
                  }
                })
            .sorted(
                Comparator.comparing(DocumentationResource::id)
                    .thenComparing(DocumentationResource::version))
            .toList();
  }

  public ResourcePage<DocumentationResource> discover(ResourceQuery query) {
    return DeterministicPaginator.page(
        documents, query, document -> document.id() + " " + document.version());
  }

  public Optional<DocumentationResource> find(String id, String version) {
    return documents.stream()
        .filter(document -> document.id().equals(id) && document.version().equals(version))
        .findFirst();
  }
}
