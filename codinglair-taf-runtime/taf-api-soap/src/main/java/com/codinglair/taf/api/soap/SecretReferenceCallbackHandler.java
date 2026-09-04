package com.codinglair.taf.api.soap;

import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import org.apache.wss4j.common.ext.WSPasswordCallback;

/** Secret-backed WSS4J callback for UsernameToken, signature, and decryption passwords. */
public final class SecretReferenceCallbackHandler implements CallbackHandler {
  private final SecretManager secrets;
  private final Map<String, String> references;
  private final SecretRequestContext context;

  public SecretReferenceCallbackHandler(
      SecretManager secrets, Map<String, String> references, SecretRequestContext context) {
    this.secrets = Objects.requireNonNull(secrets, "secrets");
    this.references = Map.copyOf(references);
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override
  public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
    for (Callback callback : callbacks) {
      if (!(callback instanceof WSPasswordCallback passwordCallback)) {
        throw new UnsupportedCallbackException(callback, "Unsupported WS-Security callback");
      }
      String reference = references.get(passwordCallback.getIdentifier());
      if (reference == null) {
        throw new IOException(
            "No secret reference is configured for the requested WS-Security identity");
      }
      try (var resolved = secrets.resolve(reference, context)) {
        passwordCallback.setPassword(resolved.useAsString());
      } catch (RuntimeException failure) {
        throw new IOException("WS-Security credential resolution failed", failure);
      }
    }
  }

  @Override
  public String toString() {
    return "SecretReferenceCallbackHandler[identities="
        + references.size()
        + ", references=REDACTED]";
  }
}
