package com.example.generated;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.lifecycle.TestSessionLifecycle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Packaged-consumer proof of isolated parallel ownership and framework cleanup. */
public final class ParallelSessionOwnershipTest
    extends com.codinglair.taf.runtime.testng.TafBaseTest {
  private static final int INVOCATIONS = 4;
  private static final CyclicBarrier OPEN = new CyclicBarrier(INVOCATIONS);
  private static final CyclicBarrier OBSERVED = new CyclicBarrier(INVOCATIONS);
  private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

  @Autowired private TestSessionLifecycle lifecycle;

  @DataProvider(name = "parallel-invocations", parallel = true)
  public Object[][] parallelInvocations() {
    return new Object[][] {{1}, {2}, {3}, {4}};
  }

  @Test(dataProvider = "parallel-invocations")
  public void ownsExactlyOneIsolatedSessionPerParallelInvocation(int invocation) throws Exception {
    assertThat(invocation).isBetween(1, INVOCATIONS);
    sessionIds.add(testSession().getArtifactCollector().getSessionId());
    OPEN.await(10, TimeUnit.SECONDS);
    assertThat(lifecycle.activeOwnerships()).hasSize(INVOCATIONS);
    assertThat(lifecycle.activeOwnerships().values())
        .extracting(TestSessionLifecycle.OwnershipDiagnostic::threadId)
        .doesNotHaveDuplicates();
    OBSERVED.await(10, TimeUnit.SECONDS);
  }

  @AfterClass(alwaysRun = true)
  public void verifiesFrameworkCleanup() {
    assertThat(sessionIds).hasSize(INVOCATIONS);
    assertThat(lifecycle.activeOwnerships()).isEmpty();
  }
}
