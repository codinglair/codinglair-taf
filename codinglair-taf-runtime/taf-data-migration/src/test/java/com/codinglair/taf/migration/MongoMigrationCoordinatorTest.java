package com.codinglair.taf.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MongoMigrationCoordinatorTest {
  @Test
  void serializesSameTargetAndAllowsDifferentTargets() throws Exception {
    var coordinator = new MongoMigrationCoordinator();
    var active = new AtomicInteger();
    var maximum = new AtomicInteger();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> hold(coordinator, "same", "history", active, maximum, entered, release));
      assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
      var attempted = new CountDownLatch(1);
      var second =
          executor.submit(
              () -> {
                attempted.countDown();
                hold(
                    coordinator,
                    "SAME",
                    "history",
                    active,
                    maximum,
                    new CountDownLatch(0),
                    new CountDownLatch(0));
              });
      assertThat(attempted.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(maximum).hasValue(1);
      release.countDown();
      first.get(2, TimeUnit.SECONDS);
      second.get(2, TimeUnit.SECONDS);
    }

    active.set(0);
    maximum.set(0);
    var bothEntered = new CountDownLatch(2);
    var bothRelease = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> hold(coordinator, "one", "history", active, maximum, bothEntered, bothRelease));
      var second =
          executor.submit(
              () -> hold(coordinator, "two", "history", active, maximum, bothEntered, bothRelease));
      assertThat(bothEntered.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(maximum).hasValue(2);
      bothRelease.countDown();
      first.get(2, TimeUnit.SECONDS);
      second.get(2, TimeUnit.SECONDS);
    }
  }

  private static void hold(
      MongoMigrationCoordinator coordinator,
      String target,
      String history,
      AtomicInteger active,
      AtomicInteger maximum,
      CountDownLatch entered,
      CountDownLatch release) {
    try (var _ = coordinator.acquire(target, history, Duration.ofSeconds(2))) {
      int count = active.incrementAndGet();
      maximum.accumulateAndGet(count, Math::max);
      entered.countDown();
      release.await(2, TimeUnit.SECONDS);
      active.decrementAndGet();
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      throw new AssertionError("interrupted");
    }
  }
}
