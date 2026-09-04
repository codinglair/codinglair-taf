package com.codinglair.taf.mcp.jobs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** JDK-only local persistence using atomic replacement and per-job OS file locks. */
public final class LocalJobRepository implements JobRepository {
  private static final int MAGIC = 0x5441464A;
  private static final int FORMAT_VERSION = 1;
  private static final ConcurrentHashMap<Path, ReentrantLock> PROCESS_LOCKS =
      new ConcurrentHashMap<>();
  private final Path root;
  private final JobLimits limits;

  public LocalJobRepository(Path root) {
    this(root, JobLimits.DEFAULTS);
  }

  public LocalJobRepository(Path root, JobLimits limits) {
    this.root = root.toAbsolutePath().normalize();
    this.limits = limits;
    try {
      Files.createDirectories(this.root);
      if (Files.isSymbolicLink(this.root)) {
        throw new IllegalArgumentException("Job repository root cannot be a symbolic link");
      }
    } catch (IOException failure) {
      throw new IllegalArgumentException("Cannot initialize local job repository", failure);
    }
  }

  @Override
  public Job create(Job job) {
    validate(job);
    return locked(
        job.id(),
        () -> {
          Path path = jobPath(job.id());
          if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new JobConflictException(job.id(), -1, read(path, job.id()).version());
          }
          write(path, job);
          return job;
        });
  }

  @Override
  public Optional<Job> find(JobId id) {
    return locked(
        id,
        () -> {
          Path path = jobPath(id);
          return Files.exists(path, LinkOption.NOFOLLOW_LINKS)
              ? Optional.of(read(path, id))
              : Optional.empty();
        });
  }

  @Override
  public Job save(Job job, long expectedVersion) {
    validate(job);
    return locked(
        job.id(),
        () -> {
          Path path = jobPath(job.id());
          if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new JobPersistenceException(
                "update missing", job.id(), new IOException("not found"));
          }
          Job current = read(path, job.id());
          if (current.version() != expectedVersion || job.version() != expectedVersion + 1) {
            throw new JobConflictException(job.id(), expectedVersion, current.version());
          }
          JobStateMachine.validateReplacement(current, job);
          write(path, job);
          return job;
        });
  }

  @Override
  public List<Job> findRecoverable() {
    try (var paths = Files.list(root)) {
      return paths
          .filter(path -> path.getFileName().toString().endsWith(".job"))
          .map(
              path ->
                  find(new JobId(
                          decodeFileName(
                              path.getFileName().toString().replaceFirst("\\.job$", ""))))
                      .orElseThrow())
          .filter(
              job ->
                  job.state() == JobState.RUNNING
                      || job.state() == JobState.RECOVERY_PENDING
                      || job.state() == JobState.CANCEL_REQUESTED)
          .toList();
    } catch (IOException failure) {
      throw new JobPersistenceException("scan", new JobId("repository"), failure);
    }
  }

  private <T> T locked(JobId id, IoSupplier<T> operation) {
    Path lockPath = resolve(id, ".lock");
    ReentrantLock processLock = PROCESS_LOCKS.computeIfAbsent(lockPath, _ -> new ReentrantLock());
    processLock.lock();
    try {
      try (FileChannel channel =
              FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
          FileLock ignored = channel.lock()) {
        return operation.get();
      } catch (JobConflictException | JobPersistenceException failure) {
        throw failure;
      } catch (IOException failure) {
        throw new JobPersistenceException("access", id, failure);
      }
    } finally {
      processLock.unlock();
    }
  }

  private Path jobPath(JobId id) {
    return resolve(id, ".job");
  }

  private Path resolve(JobId id, String suffix) {
    String fileName =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(id.value().getBytes(StandardCharsets.UTF_8));
    Path path = root.resolve(fileName + suffix).normalize();
    if (!path.getParent().equals(root)) {
      throw new IllegalArgumentException("Job path escapes repository root");
    }
    return path;
  }

  private static String decodeFileName(String encoded) {
    try {
      return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException failure) {
      throw new JobPersistenceException("decode", new JobId("repository"), failure);
    }
  }

  private Job read(Path path, JobId expectedId) {
    if (Files.isSymbolicLink(path)) {
      throw new JobPersistenceException(
          "read linked", expectedId, new IOException("symbolic link rejected"));
    }
    try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
      if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) {
        throw new IOException("unsupported or corrupt job format");
      }
      JobId id = new JobId(input.readUTF());
      if (!id.equals(expectedId)) {
        throw new IOException("job identity mismatch");
      }
      String operation = input.readUTF();
      JobState state = JobState.valueOf(input.readUTF());
      int progress = input.readInt();
      long version = input.readLong();
      Instant created = Instant.ofEpochMilli(input.readLong());
      Instant updated = Instant.ofEpochMilli(input.readLong());
      Optional<String> checkpoint =
          input.readBoolean() ? Optional.of(input.readUTF()) : Optional.empty();
      Map<String, String> payload = readMap(input);
      List<JobReference> references = readReferences(input);
      List<JobEvent> events = readEvents(input);
      if (input.read() != -1) {
        throw new IOException("unexpected trailing job data");
      }
      Job job =
          new Job(
              id,
              operation,
              payload,
              state,
              progress,
              checkpoint,
              references,
              events,
              version,
              created,
              updated);
      validate(job);
      return job;
    } catch (IOException | IllegalArgumentException failure) {
      throw new JobPersistenceException("read", expectedId, failure);
    }
  }

  private void write(Path path, Job job) {
    Path temporary = null;
    try {
      temporary = Files.createTempFile(root, job.id().value() + "-", ".tmp");
      try (var output =
          new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
        output.writeInt(MAGIC);
        output.writeInt(FORMAT_VERSION);
        output.writeUTF(job.id().value());
        output.writeUTF(job.operation());
        output.writeUTF(job.state().name());
        output.writeInt(job.progressPercent());
        output.writeLong(job.version());
        output.writeLong(job.createdAt().toEpochMilli());
        output.writeLong(job.updatedAt().toEpochMilli());
        output.writeBoolean(job.safeCheckpoint().isPresent());
        if (job.safeCheckpoint().isPresent()) output.writeUTF(job.safeCheckpoint().orElseThrow());
        writeMap(output, job.payload());
        output.writeInt(job.resultReferences().size());
        for (JobReference reference : job.resultReferences()) {
          output.writeUTF(reference.uri());
          output.writeUTF(reference.mediaType());
        }
        output.writeInt(job.events().size());
        for (JobEvent event : job.events()) {
          output.writeLong(event.sequence());
          output.writeLong(event.occurredAt().toEpochMilli());
          output.writeUTF(event.kind());
          output.writeUTF(event.message());
        }
      }
      move(temporary, path);
    } catch (IOException failure) {
      throw new JobPersistenceException("write", job.id(), failure);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // A failed cleanup is secondary to the persistence result and contains no job data.
        }
      }
    }
  }

  private static void move(Path source, Path target) throws IOException {
    try {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void validate(Job job) {
    if (job.payload().size() > limits.maximumPayloadEntries()) {
      throw new IllegalArgumentException("Job payload has too many entries");
    }
    int characters = 0;
    for (var entry : job.payload().entrySet()) {
      String key = Job.text(entry.getKey(), "payload key", 128);
      String value = Job.text(entry.getValue(), "payload value", limits.maximumPayloadCharacters());
      characters += key.length() + value.length();
    }
    if (characters > limits.maximumPayloadCharacters()) {
      throw new IllegalArgumentException("Job payload exceeds the configured character limit");
    }
    if (job.events().size() > limits.maximumEvents()
        || job.resultReferences().size() > limits.maximumReferences()) {
      throw new IllegalArgumentException("Job event or result-reference limit exceeded");
    }
    long expectedSequence = 1;
    for (JobEvent event : job.events()) {
      if (event.sequence() != expectedSequence++) {
        throw new IllegalArgumentException("Job event sequence must be contiguous");
      }
    }
  }

  private Map<String, String> readMap(DataInputStream input) throws IOException {
    int count = boundedCount(input.readInt(), limits.maximumPayloadEntries(), "payload");
    Map<String, String> values = new HashMap<>();
    for (int index = 0; index < count; index++) values.put(input.readUTF(), input.readUTF());
    return values;
  }

  private List<JobReference> readReferences(DataInputStream input) throws IOException {
    int count = boundedCount(input.readInt(), limits.maximumReferences(), "references");
    var values = new ArrayList<JobReference>(count);
    for (int index = 0; index < count; index++)
      values.add(new JobReference(input.readUTF(), input.readUTF()));
    return values;
  }

  private List<JobEvent> readEvents(DataInputStream input) throws IOException {
    int count = boundedCount(input.readInt(), limits.maximumEvents(), "events");
    var values = new ArrayList<JobEvent>(count);
    for (int index = 0; index < count; index++) {
      values.add(
          new JobEvent(
              input.readLong(),
              Instant.ofEpochMilli(input.readLong()),
              input.readUTF(),
              input.readUTF()));
    }
    return values;
  }

  private static int boundedCount(int count, int maximum, String field) throws IOException {
    if (count < 0 || count > maximum) throw new EOFException("invalid " + field + " count");
    return count;
  }

  private static void writeMap(DataOutputStream output, Map<String, String> values)
      throws IOException {
    output.writeInt(values.size());
    for (var entry : values.entrySet()) {
      output.writeUTF(entry.getKey());
      output.writeUTF(entry.getValue());
    }
  }

  @FunctionalInterface
  private interface IoSupplier<T> {
    T get() throws IOException;
  }
}
