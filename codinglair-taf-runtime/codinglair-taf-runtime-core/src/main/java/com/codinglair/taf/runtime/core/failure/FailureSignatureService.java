package com.codinglair.taf.runtime.core.failure;

import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Deterministic, redacted and bounded failure-signature:v1 implementation. */
public final class FailureSignatureService {
  private static final Pattern UUID = Pattern.compile("(?i)\\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\b");
  private static final Pattern TIMESTAMP = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}[T ][0-9:.+-]+Z?\\b");
  private static final Pattern PORT = Pattern.compile("(?<=[:=])\\d{2,5}\\b");
  private static final Pattern HEX_ADDRESS = Pattern.compile("(?i)0x[0-9a-f]+");
  private static final Pattern WINDOWS_TEMP = Pattern.compile("(?i)[a-z]:[/\\\\](?:[^\\s:/\\\\]+[/\\\\])*(?:temp|tmp)[/\\\\][^\\s]+", Pattern.CASE_INSENSITIVE);
  private static final Pattern UNIX_TEMP = Pattern.compile("/(?:tmp|var/tmp)/[^\\s]+");
  private static final Pattern LINE = Pattern.compile("(?<=\\.java:)\\d+");
  private static final Pattern CONTAINER = Pattern.compile("(?i)\\b(?:container|pod)[-_]?[a-z0-9]+(?:[-_][a-z0-9]+)*[-_][0-9a-f]{8,}\\b");
  private final RedactionPipeline redaction;
  private final int messageLimit;
  private final int frameLimit;
  private final int causeLimit;

  public FailureSignatureService() {
    this(new RedactionPipeline(), 2048, 12, 4);
  }

  public FailureSignatureService(RedactionPipeline redaction, int messageLimit, int frameLimit) {
    this(redaction, messageLimit, frameLimit, 4);
  }

  public FailureSignatureService(RedactionPipeline redaction, int messageLimit, int frameLimit, int causeLimit) {
    if (messageLimit < 1 || frameLimit < 0 || causeLimit < 1) throw new IllegalArgumentException("signature bounds must be positive");
    this.redaction = redaction;
    this.messageLimit = messageLimit;
    this.frameLimit = frameLimit;
    this.causeLimit = causeLimit;
  }

  public FailureSignature sign(FailureContext context) {
    if (context.failure() == null) return null;
    String canonical = canonicalForTesting(context);
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(StandardCharsets.UTF_8));
      return new FailureSignature("failure-signature:v1:" + HexFormat.of().formatHex(digest), "v1");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("Required SHA-256 algorithm unavailable", impossible);
    }
  }

  /** Synthetic-only package hook; production callers receive only the hash. */
  String canonicalForTesting(FailureContext context) {
    if (context.failure() == null) return "";
    Throwable failure = context.failure();
    StringBuilder canonical = new StringBuilder(1024)
        .append("capability=").append(normalize(context.capability())).append('\n')
        .append("phase=").append(normalize(context.phase())).append('\n')
        ;
    java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    Throwable cause = failure;
    for (int causeIndex = 0; cause != null && causeIndex < causeLimit && seen.add(cause); causeIndex++) {
      canonical.append("cause.type=").append(cause.getClass().getName()).append('\n')
          .append("cause.message=").append(normalize(cause.getMessage())).append('\n');
      StackTraceElement[] frames = cause.getStackTrace();
      for (int index = 0; index < Math.min(frameLimit, frames.length); index++) {
        StackTraceElement frame = frames[index];
        canonical.append("frame=").append(frame.getClassName()).append('#')
            .append(frame.getMethodName()).append('(').append(frame.getFileName()).append(")\n");
      }
      cause = cause.getCause();
    }
    return canonical.toString();
  }

  private String normalize(String input) {
    String value = redaction.redact(input == null ? "" : input);
    value = UUID.matcher(value).replaceAll("[id]");
    value = TIMESTAMP.matcher(value).replaceAll("[time]");
    value = PORT.matcher(value).replaceAll("[port]");
    value = HEX_ADDRESS.matcher(value).replaceAll("[address]");
    value = WINDOWS_TEMP.matcher(value).replaceAll("[temp-path]");
    value = UNIX_TEMP.matcher(value).replaceAll("[temp-path]");
    value = LINE.matcher(value).replaceAll("[line]");
    value = CONTAINER.matcher(value).replaceAll("[container]");
    value = value.replace('\\', '/').replaceAll("\\s+", " ").strip();
    return value.substring(0, Math.min(messageLimit, value.length()));
  }
}
