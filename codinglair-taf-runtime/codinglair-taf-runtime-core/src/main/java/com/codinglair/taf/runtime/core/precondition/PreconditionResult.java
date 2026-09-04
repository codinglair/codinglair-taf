package com.codinglair.taf.runtime.core.precondition;

import com.codinglair.taf.core.validation.ValidationResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PreconditionResult represents the outcome of a dependency-aware precondition check.
 *
 * <p>This class enables tests to:
 *
 * <ul>
 *   <li>Check preconditions before execution
 *   <li>Report precondition failures without hiding them
 *   <li>Track precondition state across parallel test executions
 * </ul>
 *
 * <p>Precondition failures remain visible in structured results as required by FR-EX-005.
 *
 * @author Codinglair TAF Team
 */
public class PreconditionResult {

  public enum PreconditionType {
    DATABASE,
    API,
    ENVIRONMENT,
    SECURITY,
    DATA,
    NETWORK,
    SERVICE,
    CUSTOM
  }

  public enum PreconditionStatus {
    SATISFIED,
    FAILED,
    SKIPPED,
    UNKNOWN
  }

  private final String preconditionKey;
  private final PreconditionType preconditionType;
  private final PreconditionStatus status;
  private final String message;
  private final Instant checkedAt;
  private final List<String> details;
  private final List<String> recommendations;
  private final Map<String, String> context;

  /**
   * Creates a precondition result with satisfied status.
   *
   * @param key precondition identifier
   * @param type precondition category
   * @return precondition result
   */
  public static PreconditionResult satisfied(String key, PreconditionType type) {
    return new PreconditionResult(
        key,
        type,
        PreconditionStatus.SATISFIED,
        "Precondition satisfied",
        Instant.now(),
        new ArrayList<>(),
        new ArrayList<>(),
        java.util.Collections.emptyMap());
  }

  /**
   * Creates a precondition result with failed status.
   *
   * @param key precondition identifier
   * @param type precondition category
   * @param message failure message
   * @return precondition result
   */
  public static PreconditionResult failed(String key, PreconditionType type, String message) {
    return new PreconditionResult(
        key,
        type,
        PreconditionStatus.FAILED,
        message,
        Instant.now(),
        new ArrayList<>(),
        new ArrayList<>(),
        java.util.Collections.emptyMap());
  }

  /**
   * Creates a precondition result with failed status and details.
   *
   * @param key precondition identifier
   * @param type precondition category
   * @param message failure message
   * @param details additional failure details
   * @return precondition result
   */
  public static PreconditionResult failed(
      String key, PreconditionType type, String message, List<String> details) {
    return new PreconditionResult(
        key,
        type,
        PreconditionStatus.FAILED,
        message,
        Instant.now(),
        details,
        new ArrayList<>(),
        java.util.Collections.emptyMap());
  }

  /**
   * Creates a precondition result with failed status and recommendations.
   *
   * @param key precondition identifier
   * @param type precondition category
   * @param message failure message
   * @param recommendations remediation recommendations
   * @return precondition result
   */
  public static PreconditionResult failedWithRecommendations(
      String key, PreconditionType type, String message, List<String> recommendations) {
    return new PreconditionResult(
        key,
        type,
        PreconditionStatus.FAILED,
        message,
        Instant.now(),
        new ArrayList<>(),
        recommendations,
        java.util.Collections.emptyMap());
  }

  /**
   * Creates a precondition result with failed status, details, and recommendations.
   *
   * @param key precondition identifier
   * @param type precondition category
   * @param message failure message
   * @param details failure details
   * @param recommendations remediation recommendations
   * @return precondition result
   */
  public static PreconditionResult failedWithDetailsAndRecommendations(
      String key,
      PreconditionType type,
      String message,
      List<String> details,
      List<String> recommendations) {
    return new PreconditionResult(
        key,
        type,
        PreconditionStatus.FAILED,
        message,
        Instant.now(),
        details,
        recommendations,
        java.util.Collections.emptyMap());
  }

  /**
   * Creates a precondition result with failed status, details, recommendations, and context.
   *
   * @param key precondition identifier
   * @param type precondition category
   * @param message failure message
   * @param details failure details
   * @param recommendations remediation recommendations
   * @param context contextual metadata
   * @return precondition result
   */
  public static PreconditionResult failedWithDetailsAndRecommendations(
      String key,
      PreconditionType type,
      String message,
      List<String> details,
      List<String> recommendations,
      Map<String, String> context) {
    return new PreconditionResult(
        key,
        type,
        PreconditionStatus.FAILED,
        message,
        Instant.now(),
        details,
        recommendations,
        context);
  }

  /**
   * Creates a precondition result with skipped status.
   *
   * @param key precondition identifier
   * @param type precondition category
   * @return precondition result
   */
  public static PreconditionResult skipped(String key, PreconditionType type) {
    return new PreconditionResult(
        key,
        type,
        PreconditionStatus.SKIPPED,
        "Precondition skipped",
        Instant.now(),
        new ArrayList<>(),
        new ArrayList<>(),
        java.util.Collections.emptyMap());
  }

  /**
   * Creates a precondition result with unknown status.
   *
   * @param key precondition identifier
   * @param type precondition category
   * @return precondition result
   */
  public static PreconditionResult unknown(String key, PreconditionType type) {
    return new PreconditionResult(
        key,
        type,
        PreconditionStatus.UNKNOWN,
        "Precondition status unknown",
        Instant.now(),
        new ArrayList<>(),
        new ArrayList<>(),
        java.util.Collections.emptyMap());
  }

  /**
   * Creates a precondition result with unknown status and message.
   *
   * @param key precondition identifier
   * @param type precondition category
   * @param message unknown status message
   * @return precondition result
   */
  public static PreconditionResult unknown(String key, PreconditionType type, String message) {
    return new PreconditionResult(
        key,
        type,
        PreconditionStatus.UNKNOWN,
        message,
        Instant.now(),
        new ArrayList<>(),
        new ArrayList<>(),
        java.util.Collections.emptyMap());
  }

  /**
   * Creates a precondition result with unknown status, message, and context.
   *
   * @param key precondition identifier
   * @param type precondition category
   * @param message unknown status message
   * @param context contextual information
   * @return precondition result
   */
  public static PreconditionResult unknown(
      String key, PreconditionType type, String message, Map<String, String> context) {
    return new PreconditionResult(
        key,
        type,
        PreconditionStatus.UNKNOWN,
        message,
        Instant.now(),
        new ArrayList<>(),
        new ArrayList<>(),
        context);
  }

  private PreconditionResult(
      String preconditionKey,
      PreconditionType preconditionType,
      PreconditionStatus status,
      String message,
      Instant checkedAt,
      List<String> details,
      List<String> recommendations,
      Map<String, String> context) {

    this.preconditionKey = preconditionKey;
    this.preconditionType = preconditionType;
    this.status = status;
    this.message = message;
    this.checkedAt = checkedAt;
    this.details = details;
    this.recommendations = recommendations;
    this.context = context;
  }

  /**
   * Gets the precondition identifier.
   *
   * @return precondition key
   */
  public String getPreconditionKey() {
    return preconditionKey;
  }

  /**
   * Gets the precondition type/category.
   *
   * @return precondition type
   */
  public PreconditionType getPreconditionType() {
    return preconditionType;
  }

  /**
   * Gets the precondition status.
   *
   * @return precondition status
   */
  public PreconditionStatus getStatus() {
    return status;
  }

  /**
   * Gets the status message.
   *
   * @return message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Gets when the precondition was checked.
   *
   * @return check timestamp
   */
  public Instant getCheckedAt() {
    return checkedAt;
  }

  /**
   * Gets failure details if status is FAILED.
   *
   * @return list of details (empty if not failed)
   */
  public List<String> getDetails() {
    return details;
  }

  /**
   * Gets remediation recommendations if status is FAILED.
   *
   * @return list of recommendations (empty if not failed)
   */
  public List<String> getRecommendations() {
    return recommendations;
  }

  /**
   * Gets contextual metadata.
   *
   * @return context map
   */
  public Map<String, String> getContext() {
    return context;
  }

  /**
   * Converts to ValidationResult for framework integration.
   *
   * @return equivalent ValidationResult
   */
  public ValidationResult asValidationResult() {
    if (status == PreconditionStatus.SATISFIED) {
      return ValidationResult.success(preconditionKey, "Precondition satisfied: " + message);
    }

    String hints = !recommendations.isEmpty() ? " " + String.join(", ", recommendations) : "";
    return ValidationResult.failure(preconditionKey, message + hints);
  }

  /**
   * Creates a copy with updated status.
   *
   * @param newStatus new status
   * @param newMessage new message
   * @return new precondition result
   */
  public PreconditionResult withStatus(PreconditionStatus newStatus, String newMessage) {
    return new PreconditionResult(
        preconditionKey,
        preconditionType,
        newStatus,
        newMessage,
        checkedAt,
        status == PreconditionStatus.FAILED ? new ArrayList<>(details) : new ArrayList<>(),
        status == PreconditionStatus.FAILED ? new ArrayList<>(recommendations) : new ArrayList<>(),
        context);
  }

  @Override
  public String toString() {
    return "PreconditionResult{"
        + "preconditionKey='"
        + preconditionKey
        + '\''
        + ", type="
        + preconditionType
        + ", status="
        + status
        + ", message='"
        + message
        + '\''
        + ", checkedAt="
        + checkedAt
        + ", details="
        + details
        + ", recommendations="
        + recommendations
        + '}';
  }
}
