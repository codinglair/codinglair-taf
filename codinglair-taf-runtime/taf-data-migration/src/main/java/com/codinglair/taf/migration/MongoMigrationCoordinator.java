package com.codinglair.taf.migration;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Process-local single-owner coordination for one normalized target/history pair. */
final class MongoMigrationCoordinator {
  private final ConcurrentHashMap<Key, ReentrantLock> locks = new ConcurrentHashMap<>();

  Lease acquire(String target, String history, Duration timeout) throws InterruptedException {
    Key key = new Key(normalize(target), normalize(history));
    ReentrantLock lock = locks.computeIfAbsent(key, _ -> new ReentrantLock());
    if (!lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
      removeIfUnused(key, lock);
      throw new IllegalStateException("Timed out waiting for the logical Mongo migration owner");
    }
    return new Lease(key, lock);
  }

  private void removeIfUnused(Key key, ReentrantLock lock) {
    if (!lock.isLocked() && !lock.hasQueuedThreads()) locks.remove(key, lock);
  }

  private static String normalize(String value) {
    String result =
        Objects.requireNonNull(value, "identity").trim().toLowerCase(java.util.Locale.ROOT);
    if (result.isBlank()
        || result.length() > 256
        || result.contains("\n")
        || result.contains("\r")) {
      throw new IllegalArgumentException("Migration coordination identity is invalid");
    }
    return result;
  }

  final class Lease implements AutoCloseable {
    private final Key key;
    private final ReentrantLock lock;
    private boolean closed;

    private Lease(Key key, ReentrantLock lock) {
      this.key = key;
      this.lock = lock;
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      lock.unlock();
      removeIfUnused(key, lock);
    }
  }

  private record Key(String target, String history) {}
}
