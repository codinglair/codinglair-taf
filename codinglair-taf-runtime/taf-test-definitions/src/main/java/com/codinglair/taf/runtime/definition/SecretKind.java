package com.codinglair.taf.runtime.definition;

/** Security classification for a secret-bearing test-definition value. */
public enum SecretKind {
  PASSWORD,
  API_KEY,
  ACCESS_TOKEN,
  REFRESH_TOKEN,
  CLIENT_SECRET,
  PRIVATE_KEY,
  DATABASE_PASSWORD,
  OTHER
}
