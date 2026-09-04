package com.codinglair.taf.runtime.environment;

/** Standard check categories; providers may use CUSTOM for technology-specific checks. */
public enum PreflightCheckType {
  ENDPOINT,
  READINESS,
  DNS,
  NETWORK_CONNECTIVITY,
  AUTHENTICATION,
  DIRECTORY,
  PERMISSION,
  DATABASE_SCHEMA,
  KAFKA_BROKER_TOPIC,
  RABBITMQ_EXCHANGE_QUEUE,
  BROWSER,
  APPIUM_SERVER,
  DEVICE,
  TEST_ACCOUNT,
  TEST_DATA,
  CUSTOM
}
