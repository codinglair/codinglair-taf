package com.codinglair.taf.runtime.core.condition;

import com.codinglair.taf.core.Error;
import com.codinglair.taf.core.annotation.reporting.TafDescription;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * AwaitableAssertion provides polling-based eventual consistency assertions.
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>Configurable polling intervals
 *   <li>Timeout tracking with last observed value
 *   <li>Failure categorization (timeout, predicate failure, etc.)
 * </ul>
 *
 * <p>As required by FR-EX-008, polling timeout messages contain the last observed value and timing
 * information.
 *
 * @author Codinglair TAF Team
 */
public class AwaitableAssertion {

  /** Assertion status enumeration. */
  public enum Status {
    PENDING,
    SUCCESS,
    FAILED,
    TIMEOUT
  }

  private final String assertionKey;
  private final Predicate<Object> predicate;
  private final Duration timeout;
  private final Duration pollInterval;
  private final Instant startTime;
  private Instant endTime;
  private Status status;
  private Object lastObservedValue;
  private int pollCount;
  private final List<Throwable> errors;
  private final List<Object> observedValues;

  /**
   * Creates an awaitable assertion with default settings.
   *
   * @param key assertion key
   * @param predicate predicate to evaluate
   * @return new assertion
   */
  public static AwaitableAssertion create(String key, Predicate<Object> predicate) {
    return new AwaitableAssertion(key, predicate, Duration.ofSeconds(30), Duration.ofMillis(100));
  }

  /**
   * Creates an awaitable assertion with custom settings.
   *
   * @param key assertion key
   * @param predicate predicate to evaluate
   * @param timeout maximum wait time
   * @param pollInterval polling interval
   * @return new assertion
   */
  public static AwaitableAssertion create(
      String key, Predicate<Object> predicate, Duration timeout, Duration pollInterval) {
    return new AwaitableAssertion(key, predicate, timeout, pollInterval);
  }

  private AwaitableAssertion(
      String assertionKey, Predicate<Object> predicate, Duration timeout, Duration pollInterval) {

    this.assertionKey = assertionKey;
    this.predicate = predicate;
    this.timeout = timeout;
    this.pollInterval = pollInterval;
    this.startTime = Instant.now();
    this.endTime = null;
    this.status = Status.PENDING;
    this.lastObservedValue = null;
    this.pollCount = 0;
    this.errors = new ArrayList<>();
    this.observedValues = new ArrayList<>();
  }

  /**
   * Waits for the predicate to succeed within the timeout.
   *
   * @return assertion result
   */
  @TafDescription("Wait for assertion to become true within timeout")
  public AwaitableAssertionResult await() {
    return await(null);
  }

  /**
   * Waits for the predicate to succeed within the timeout, optionally with a value provider.
   *
   * @param valueProvider optional supplier for the value to test against
   * @return assertion result
   */
  @TafDescription("Wait for assertion to become true within timeout with value provider")
  public AwaitableAssertionResult await(Supplier<Object> valueProvider) {
    return await(valueProvider, null);
  }

  /**
   * Waits for the predicate to succeed within the timeout with context.
   *
   * @param valueProvider optional supplier for the value to test against
   * @param context optional context for assertion
   * @return assertion result
   */
  @TafDescription("Wait for assertion to become true within timeout with value and context")
  public AwaitableAssertionResult await(Supplier<Object> valueProvider, String context) {
    Instant start = Instant.now();
    Object value = valueProvider != null ? valueProvider.get() : null;

    while (true) {
      try {
        Instant now = Instant.now();
        Duration elapsed = Duration.between(startTime, now);

        if (elapsed.compareTo(timeout) >= 0) {
          // Timeout occurred
          endTime = Instant.now();
          status = Status.TIMEOUT;
          // Track the last observed value from the polling loop
          lastObservedValue =
              observedValues.isEmpty() ? value : observedValues.get(observedValues.size() - 1);
          pollCount =
              pollInterval.toMillis() > 0
                  ? (int)
                          ((endTime.toEpochMilli() - startTime.toEpochMilli())
                              / pollInterval.toMillis())
                      + 1
                  : 1;
          String message =
              "Assertion timed out after "
                  + pollCount
                  + " polls. Last observed value: "
                  + lastObservedValue;
          return new AwaitableAssertionResult(
              assertionKey,
              Status.TIMEOUT,
              startTime,
              endTime,
              lastObservedValue,
              pollCount,
              message,
              errors);
        }

        if (value != null && predicate.test(value)) {
          endTime = Instant.now();
          status = Status.SUCCESS;
          pollCount++;
          String message = "Assertion succeeded after " + pollCount + " polls. Value: " + value;
          return new AwaitableAssertionResult(
              assertionKey, Status.SUCCESS, startTime, endTime, value, pollCount, message, errors);
        } else if (value == null && predicate.test(null)) {
          // Predicate provides the value to check (e.g., checking a condition directly)
          endTime = Instant.now();
          status = Status.SUCCESS;
          pollCount++;
          String message = "Assertion succeeded after " + pollCount + " polls. Value: null";
          return new AwaitableAssertionResult(
              assertionKey, Status.SUCCESS, startTime, endTime, null, pollCount, message, errors);
        }

        // Polling interval
        if (pollInterval.toMillis() > 0) {
          try {
            Thread.sleep(pollInterval.toMillis());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            endTime = Instant.now();
            status = Status.FAILED;
            // Track the last observed value before failure
            lastObservedValue =
                observedValues.isEmpty() ? value : observedValues.get(observedValues.size() - 1);
            errors.add(e);
            pollCount++;
            String message =
                "Assertion interrupted after "
                    + pollCount
                    + " polls. "
                    + (observedValues.isEmpty()
                        ? "No value observed."
                        : "Last observed value: " + lastObservedValue);
            return new AwaitableAssertionResult(
                assertionKey,
                Status.FAILED,
                startTime,
                endTime,
                lastObservedValue,
                pollCount,
                message,
                errors);
          }
        }

        observedValues.add(value);
        pollCount++;

      } catch (Exception e) {
        errors.add(e);
        endTime = Instant.now();
        status = Status.FAILED;
        // Track the last observed value before failure
        lastObservedValue =
            observedValues.isEmpty() ? value : observedValues.get(observedValues.size() - 1);
        pollCount++;
        String message =
            "Assertion failed with exception: " + e.getClass().getName() + ": " + e.getMessage();
        return new AwaitableAssertionResult(
            assertionKey,
            Status.FAILED,
            startTime,
            endTime,
            lastObservedValue,
            pollCount,
            message,
            errors);
      }
    }
  }

  /**
   * Gets the assertion key.
   *
   * @return assertion key
   */
  public String getAssertionKey() {
    return assertionKey;
  }

  /**
   * Gets the assertion status.
   *
   * @return status
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Gets the last observed value.
   *
   * @return last observed value or null
   */
  public Object getLastObservedValue() {
    return lastObservedValue;
  }

  /**
   * Gets the number of polls performed.
   *
   * @return poll count
   */
  public int getPollCount() {
    return pollCount;
  }

  /**
   * Gets the errors encountered.
   *
   * @return list of errors
   */
  public List<Throwable> getErrors() {
    return errors;
  }

  /**
   * Gets the observed values history.
   *
   * @return list of observed values
   */
  public List<Object> getObservedValues() {
    return observedValues;
  }

  /**
   * Gets the start time.
   *
   * @return start time
   */
  public Instant getStartTime() {
    return startTime;
  }

  /**
   * Gets the end time if completed.
   *
   * @return end time or null
   */
  public Instant getEndTime() {
    return endTime;
  }

  /**
   * Gets the timeout duration.
   *
   * @return timeout
   */
  public Duration getTimeout() {
    return timeout;
  }

  /**
   * Gets the poll interval.
   *
   * @return poll interval
   */
  public Duration getPollInterval() {
    return pollInterval;
  }

  /**
   * Creates an assertion result.
   *
   * @return assertion result
   */
  public AwaitableAssertionResult toResult() {
    return new AwaitableAssertionResult(
        assertionKey,
        status,
        startTime,
        endTime,
        lastObservedValue,
        pollCount,
        generateMessage(),
        errors);
  }

  private String generateMessage() {
    switch (status) {
      case SUCCESS:
        return "Assertion succeeded after " + pollCount + " polls. Value: " + lastObservedValue;
      case TIMEOUT:
        return "Assertion timed out after "
            + pollCount
            + " polls. Last observed value: "
            + lastObservedValue;
      case FAILED:
        if (!errors.isEmpty()) {
          return "Assertion failed with exception: "
              + errors.get(0).getClass().getName()
              + ": "
              + errors.get(0).getMessage();
        }
        return "Assertion failed. Last observed value: " + lastObservedValue;
      default:
        return "Assertion status: " + status;
    }
  }

  @Override
  public String toString() {
    return "AwaitableAssertion{"
        + "key='"
        + assertionKey
        + '\''
        + ", status="
        + status
        + ", lastObservedValue="
        + lastObservedValue
        + ", pollCount="
        + pollCount
        + ", errors="
        + errors
        + '}';
  }

  /** Assertion result containing the outcome. */
  public static class AwaitableAssertionResult {

    private final String key;
    private final Status status;
    private final Instant startTime;
    private final Instant endTime;
    private final Object lastObservedValue;
    private final int pollCount;
    private final String message;
    private final List<Throwable> errors;

    private AwaitableAssertionResult(
        String key,
        Status status,
        Instant startTime,
        Instant endTime,
        Object lastObservedValue,
        int pollCount,
        String message,
        List<Throwable> errors) {
      this.key = key;
      this.status = status;
      this.startTime = startTime;
      this.endTime = endTime;
      this.lastObservedValue = lastObservedValue;
      this.pollCount = pollCount;
      this.message = message;
      this.errors = errors;
    }

    /**
     * Gets the assertion key.
     *
     * @return key
     */
    public String getKey() {
      return key;
    }

    /**
     * Gets the assertion status.
     *
     * @return status
     */
    public Status getStatus() {
      return status;
    }

    /**
     * Gets the start time.
     *
     * @return start time
     */
    public Instant getStartTime() {
      return startTime;
    }

    /**
     * Gets the end time.
     *
     * @return end time
     */
    public Instant getEndTime() {
      return endTime;
    }

    /**
     * Gets the last observed value.
     *
     * @return last observed value
     */
    public Object getLastObservedValue() {
      return lastObservedValue;
    }

    /**
     * Gets the number of polls.
     *
     * @return poll count
     */
    public int getPollCount() {
      return pollCount;
    }

    /**
     * Gets the result message.
     *
     * @return message
     */
    public String getMessage() {
      return message;
    }

    /**
     * Gets the errors.
     *
     * @return errors
     */
    public List<Throwable> getErrors() {
      return errors;
    }

    /**
     * Checks if the assertion succeeded.
     *
     * @return true if succeeded
     */
    public boolean isSuccessful() {
      return status == Status.SUCCESS;
    }

    /**
     * Checks if the assertion timed out.
     *
     * @return true if timed out
     */
    public boolean isTimedOut() {
      return status == Status.TIMEOUT;
    }

    /**
     * Checks if the assertion failed.
     *
     * @return true if failed
     */
    public boolean isFailed() {
      return status == Status.FAILED;
    }

    /**
     * Creates a structured error for reporting.
     *
     * @return error if any, null otherwise
     */
    public Error toError() {
      if (status != Status.SUCCESS && !errors.isEmpty()) {
        return new Error(
            Error.ErrorType.OTHER,
            String.format("Assertion %s failed: %s", key, message),
            "Review the assertion condition and the observed system state.");
      }
      return null;
    }

    @Override
    public String toString() {
      return "AwaitableAssertionResult{"
          + "key='"
          + key
          + '\''
          + ", status="
          + status
          + ", endTime="
          + endTime
          + ", lastObservedValue="
          + lastObservedValue
          + ", pollCount="
          + pollCount
          + ", message='"
          + message
          + '\''
          + '}';
    }
  }
}
