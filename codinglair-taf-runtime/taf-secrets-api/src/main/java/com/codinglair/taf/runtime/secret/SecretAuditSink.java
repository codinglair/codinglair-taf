package com.codinglair.taf.runtime.secret;

@FunctionalInterface
public interface SecretAuditSink {
  SecretAuditSink NO_OP = event -> {};

  void record(SecretAuditEvent event);
}
