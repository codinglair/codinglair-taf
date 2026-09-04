package com.codinglair.taf.runtime.cucumber.suites;

import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;

public final class OrdinaryTechnicalTest {
  public static final AtomicInteger INVOCATIONS = new AtomicInteger();

  @Test
  public void technicalBoundaryCheck() {
    INVOCATIONS.incrementAndGet();
  }
}
