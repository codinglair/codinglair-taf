package com.codinglair.taf.runtime.testng;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.lifecycle.InvocationDescriptor;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.testng.IAttributes;

/** Stores sessions on the TestNG object that owns their lifecycle. */
public final class SessionFactory {
  static final String SESSION_KEY = SessionFactory.class.getName() + ".session";
  static final String ARTIFACTS_KEY = SessionFactory.class.getName() + ".artifacts";

  private SessionFactory() {}

  static InvocationDescriptor descriptor(org.testng.ITestResult result) {
    String id = result.getMethod().getQualifiedName() + "|" + System.identityHashCode(result);
    return new InvocationDescriptor(
        id, result.getMethod().getMethodName(), result.getTestClass().getRealClass().getName());
  }

  static void bindObservation(IAttributes owner, TestSession session) {
    owner.setAttribute(SESSION_KEY, session);
  }

  static void unbindObservation(IAttributes owner) {
    owner.removeAttribute(SESSION_KEY);
  }

  public static TestSession getCurrentSession(IAttributes owner) {
    java.util.Objects.requireNonNull(owner, "owner");
    Object session = owner.getAttribute(SESSION_KEY);
    if (!(session instanceof TestSession testSession)) {
      throw new IllegalStateException("No TestSession available for this TestNG scope");
    }
    return testSession;
  }

  public static boolean hasSession(IAttributes owner) {
    return owner != null && owner.getAttribute(SESSION_KEY) instanceof TestSession;
  }

  /** Attaches sanitized reporting evidence to the current invocation attempt. */
  public static void addArtifact(IAttributes owner, TestArtifact artifact) {
    java.util.Objects.requireNonNull(owner, "owner");
    java.util.Objects.requireNonNull(artifact, "artifact");
    synchronized (owner) {
      @SuppressWarnings("unchecked")
      List<TestArtifact> artifacts =
          owner.getAttribute(ARTIFACTS_KEY) instanceof List<?> existing
              ? (List<TestArtifact>) existing
              : null;
      if (artifacts == null) {
        artifacts = new CopyOnWriteArrayList<>();
        owner.setAttribute(ARTIFACTS_KEY, artifacts);
      }
      artifacts.add(artifact);
    }
  }

  static List<TestArtifact> snapshotArtifacts(IAttributes owner) {
    Object value = owner.getAttribute(ARTIFACTS_KEY);
    if (!(value instanceof List<?> artifacts)) {
      return List.of();
    }
    return artifacts.stream().map(TestArtifact.class::cast).toList();
  }
}
