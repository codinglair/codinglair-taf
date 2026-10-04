package com.codinglair.taf.runtime.definition.mongodb;

import com.codinglair.taf.runtime.definition.PayloadException;
import com.codinglair.taf.runtime.definition.PayloadRange;
import com.codinglair.taf.runtime.definition.PayloadReference;
import com.codinglair.taf.runtime.definition.PayloadResolver;
import com.codinglair.taf.runtime.definition.ResolvedPayload;
import com.codinglair.taf.runtime.file.FilePayloadResolver;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.model.GridFSFile;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bson.Document;
import org.bson.types.ObjectId;

/** Optional GridFS payload adapter. Downloads to a bounded temporary file before delivery. */
public final class GridFsPayloadResolver implements PayloadResolver {
  private final GridFSBucket bucket;
  private final Path temporaryDirectory;
  private final long maximumSize;

  public GridFsPayloadResolver(GridFSBucket bucket, Path temporaryDirectory, long maximumSize) {
    this.bucket = bucket;
    this.temporaryDirectory = temporaryDirectory;
    this.maximumSize = maximumSize;
  }

  @Override
  public ResolvedPayload open(PayloadReference reference) {
    if (reference.size() == 0) return stage(reference, null);
    return open(reference, PayloadRange.all(reference.size()));
  }

  @Override
  public ResolvedPayload open(PayloadReference reference, PayloadRange range) {
    return stage(reference, range);
  }

  private ResolvedPayload stage(PayloadReference reference, PayloadRange range) {
    ObjectId id = id(reference);
    GridFSFile file = bucket.find(new Document("_id", id)).first();
    validateMetadata(file, reference);
    Path staged = null;
    try {
      staged = download(file, id);
      ResolvedPayload delegate = openStaged(staged, reference, range);
      return managed(delegate, staged);
    } catch (PayloadException failure) {
      cleanup(staged);
      throw failure;
    } catch (IOException _) {
      cleanup(staged);
      throw failure(PayloadException.Kind.IO, reference);
    }
  }

  private void validateMetadata(GridFSFile file, PayloadReference reference) {
    if (file == null) throw failure(PayloadException.Kind.MISSING, reference);
    if (file.getLength() > maximumSize || file.getLength() > reference.size())
      throw failure(PayloadException.Kind.OVERSIZED, reference);
    String contentType =
        file.getMetadata() == null ? null : file.getMetadata().getString("contentType");
    if (!reference.mediaType().equalsIgnoreCase(contentType))
      throw failure(PayloadException.Kind.MEDIA_TYPE, reference);
  }

  private Path download(GridFSFile file, ObjectId id) throws IOException {
    Files.createDirectories(temporaryDirectory);
    String filename = file.getFilename();
    int extensionAt = filename.lastIndexOf('.');
    String suffix = extensionAt < 0 ? ".bin" : filename.substring(extensionAt);
    Path staged = Files.createTempFile(temporaryDirectory, "taf-payload-", suffix);
    try {
      try (OutputStream output = Files.newOutputStream(staged)) {
        bucket.downloadToStream(id, output);
      }
      return staged;
    } catch (IOException failure) {
      cleanup(staged);
      throw failure;
    }
  }

  private ResolvedPayload openStaged(Path staged, PayloadReference reference, PayloadRange range) {
    PayloadReference local =
        new PayloadReference(
            reference.logicalId(),
            staged.toUri(),
            reference.checksum(),
            reference.mediaType(),
            reference.size(),
            reference.version());
    var resolver = new FilePayloadResolver(temporaryDirectory, maximumSize);
    return range == null ? resolver.open(local) : resolver.open(local, range);
  }

  private static ResolvedPayload managed(ResolvedPayload delegate, Path staged) {
    return new ResolvedPayload(
        delegate.evidence(),
        delegate.range(),
        delegate.stream(),
        () -> {
          delegate.close();
          cleanup(staged);
        });
  }

  private static void cleanup(Path staged) {
    if (staged == null) return;
    try {
      Files.deleteIfExists(staged);
    } catch (IOException _) {
      staged.toFile().deleteOnExit();
    }
  }

  private static ObjectId id(PayloadReference reference) {
    URI location = reference.location();
    if (!"gridfs".equals(location.getScheme()))
      throw failure(PayloadException.Kind.PATH_SAFETY, reference);
    String value = location.getSchemeSpecificPart();
    if (value.startsWith("//")) value = value.substring(2);
    if (!ObjectId.isValid(value)) throw failure(PayloadException.Kind.PATH_SAFETY, reference);
    return new ObjectId(value);
  }

  private static PayloadException failure(PayloadException.Kind kind, PayloadReference reference) {
    return new PayloadException(
        kind,
        reference.logicalId(),
        "payload "
            + reference.logicalId()
            + " failed "
            + kind.name().toLowerCase()
            + " validation");
  }
}
