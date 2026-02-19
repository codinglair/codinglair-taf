package com.codinglair.taf.core.test;

/**
 * The container for an instance of TestLifecycleManager ensuring a parallel execution
 * safety. It's a common source utilized by aspects regardless of who is the owner and
 * an invoker of the TestLifecycleManager instance. It ensures the aspects and annotations
 * will work in both TestNG functional tests and BDD tests.
 */
public class TestLifecycleContainer {
    private static final ThreadLocal<TestLifecycleManager> managerStore = new ThreadLocal<>();

    public static void setManager(TestLifecycleManager manager) {
        managerStore.set(manager);
    }

    public static TestLifecycleManager getManager() {
        return managerStore.get();
    }

    public static void clear() {
        if(managerStore != null)
            managerStore.remove();
    }
}
