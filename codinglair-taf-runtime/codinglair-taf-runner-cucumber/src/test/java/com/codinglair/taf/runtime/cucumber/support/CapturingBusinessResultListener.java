package com.codinglair.taf.runtime.cucumber.support;

import com.codinglair.taf.runtime.cucumber.CucumberBusinessResult;
import com.codinglair.taf.runtime.cucumber.CucumberBusinessResultListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CapturingBusinessResultListener implements CucumberBusinessResultListener {
  private static final List<CucumberBusinessResult> RESULTS = new CopyOnWriteArrayList<>();

  @Override
  public void onResult(CucumberBusinessResult result) {
    RESULTS.add(result);
  }

  public static void reset() {
    RESULTS.clear();
  }

  public static List<CucumberBusinessResult> results() {
    return List.copyOf(RESULTS);
  }
}
