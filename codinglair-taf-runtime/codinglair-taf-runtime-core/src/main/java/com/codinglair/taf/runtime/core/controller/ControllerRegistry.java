package com.codinglair.taf.runtime.core.controller;

import com.codinglair.taf.runtime.core.reporting.ReportingActionInterceptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe typed, named registry with lazy initialization. */
public final class ControllerRegistry implements AutoCloseable {
  private final Map<ControllerIdentity, Entry<?>> entries = new ConcurrentHashMap<>();
  private final List<Entry<?>> acquisitionOrder = Collections.synchronizedList(new ArrayList<>());
  private volatile ControllerContext context;
  private volatile ReportingActionInterceptor reportingInterceptor;

  public ControllerRegistry() {}

  public ControllerRegistry(ControllerContext context) {
    this.context = Objects.requireNonNull(context);
  }

  public void attach(ControllerContext context) {
    this.context = Objects.requireNonNull(context);
  }

  public void enableReporting(ReportingActionInterceptor interceptor) {
    this.reportingInterceptor = Objects.requireNonNull(interceptor, "interceptor");
  }

  public <T extends TestController> void register(Class<T> type, String name, T controller) {
    Objects.requireNonNull(controller, "controller");
    ControllerIdentity key = new ControllerIdentity(type, name);
    if (!type.isInstance(controller))
      throw new IllegalArgumentException("Controller does not implement " + type.getName());
    if (entries.putIfAbsent(key, new Entry<>(controller)) != null)
      throw new IllegalStateException("Controller already registered: " + key);
  }

  public <T extends TestController> T get(Class<T> type, String name) {
    ControllerIdentity key = new ControllerIdentity(type, name);
    Entry<?> raw = entries.get(key);
    if (raw == null) throw new ControllerNotFoundException(key);
    Entry<T> entry = cast(raw);
    entry.initialize(context(), acquisitionOrder);
    return entry.exposed(type, reportingInterceptor);
  }

  public boolean hasController(Class<? extends TestController> type, String name) {
    return entries.containsKey(new ControllerIdentity(type, name));
  }

  @Override
  public void close() {
    List<Entry<?>> acquired;
    synchronized (acquisitionOrder) {
      acquired = new ArrayList<>(acquisitionOrder);
    }
    Collections.reverse(acquired);
    Throwable failure = null;
    for (Entry<?> entry : acquired) {
      try {
        entry.close();
      } catch (Throwable current) {
        if (failure == null) failure = current;
        else failure.addSuppressed(current);
      }
    }
    if (failure != null) throwUnchecked(failure);
  }

  private ControllerContext context() {
    ControllerContext current = context;
    if (current == null)
      throw new IllegalStateException(
          "ControllerRegistry is not attached to a TestSession context");
    return current;
  }

  @SuppressWarnings("unchecked")
  private static <T extends TestController> Entry<T> cast(Entry<?> entry) {
    return (Entry<T>) entry;
  }

  private static void throwUnchecked(Throwable failure) {
    ControllerRegistry.<RuntimeException>throwAny(failure);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAny(Throwable failure) throws E {
    throw (E) failure;
  }

  private static final class Entry<T extends TestController> {
    private final T controller;
    private volatile T exposed;
    private final AtomicReference<ControllerState> state =
        new AtomicReference<>(ControllerState.NEW);

    private Entry(T controller) {
      this.controller = controller;
    }

    private T exposed(Class<T> type, ReportingActionInterceptor interceptor) {
      if (interceptor == null || !type.isInterface()) return controller;
      T current = exposed;
      if (current != null) return current;
      synchronized (this) {
        if (exposed == null) {
          Object proxy =
              Proxy.newProxyInstance(
                  type.getClassLoader(),
                  new Class<?>[] {type},
                  (ignored, method, arguments) -> {
                    java.lang.reflect.Method target =
                        controller
                            .getClass()
                            .getMethod(method.getName(), method.getParameterTypes());
                    if (!target.canAccess(controller)) target.trySetAccessible();
                    try {
                      return interceptor.invoke(
                          target,
                          arguments,
                          () -> {
                            try {
                              return target.invoke(controller, arguments);
                            } catch (InvocationTargetException failure) {
                              throwAny(failure.getCause());
                              return null;
                            }
                          });
                    } catch (Throwable failure) {
                      throwAny(failure);
                      return null;
                    }
                  });
          exposed = type.cast(proxy);
        }
        return exposed;
      }
    }

    private void initialize(ControllerContext context, List<Entry<?>> order) {
      if (state.get() == ControllerState.READY) return;
      synchronized (this) {
        if (state.get() == ControllerState.READY) return;
        if (state.get() == ControllerState.FAILED)
          throw new IllegalStateException(
              "Controller initialization previously failed: " + controller.identity());
        if (state.get() == ControllerState.CLOSED)
          throw new IllegalStateException("Controller is closed: " + controller.identity());
        state.set(ControllerState.INITIALIZING);
        try {
          controller.initialize(context);
          if (controller.state() != ControllerState.READY)
            throw new IllegalStateException(
                "Controller did not become READY: " + controller.identity());
          state.set(ControllerState.READY);
          order.add(this);
        } catch (Throwable failure) {
          state.set(ControllerState.FAILED);
          try {
            controller.close();
          } catch (Throwable cleanup) {
            failure.addSuppressed(cleanup);
          }
          throwUnchecked(failure);
        }
      }
    }

    private void close() {
      if (state.getAndSet(ControllerState.CLOSED) != ControllerState.CLOSED) controller.close();
    }
  }

  public static final class ControllerNotFoundException extends RuntimeException {
    public ControllerNotFoundException(ControllerIdentity identity) {
      super(
          "Controller not found: "
              + identity.type().getName()
              + " named '"
              + identity.name()
              + "'");
    }
  }
}
