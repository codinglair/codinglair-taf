package com.codinglair.taf.api.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.runtime.secret.ResolvedSecret;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;
import java.io.IOException;
import java.util.Map;
import javax.security.auth.callback.NameCallback;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Secret-backed WSS4J callback")
class SecretReferenceCallbackHandlerTest {
  private static final String CANARY = "private-signing-canary";

  @Test
  @DisplayName("supplies a transient signing password without exposing it in diagnostics")
  void resolvesSigningPassword() throws Exception {
    SecretManager manager = manager();
    var handler =
        new SecretReferenceCallbackHandler(
            manager, Map.of("signing-key", "secret://env/SIGNING_PASSWORD"), context());
    var callback = new WSPasswordCallback("signing-key", WSPasswordCallback.SIGNATURE);
    handler.handle(new javax.security.auth.callback.Callback[] {callback});
    assertThat(callback.getPassword()).isEqualTo(CANARY);
    assertThat(handler.toString()).doesNotContain(CANARY, "SIGNING_PASSWORD");
  }

  @Test
  @DisplayName("fails safely without echoing an unknown identity or secret")
  void missingReferenceIsSafe() {
    var handler = new SecretReferenceCallbackHandler(manager(), Map.of(), context());
    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                handler.handle(
                    new javax.security.auth.callback.Callback[] {
                      new WSPasswordCallback("unknown-private-alias", WSPasswordCallback.DECRYPT)
                    }));
    assertThat(failure)
        .hasMessageNotContaining("unknown-private-alias")
        .hasMessageNotContaining(CANARY);
  }

  @Test
  @DisplayName("rejects non-WSS4J callbacks")
  void rejectsUnsupportedCallback() {
    var handler = new SecretReferenceCallbackHandler(manager(), Map.of(), context());
    assertThrows(
        javax.security.auth.callback.UnsupportedCallbackException.class,
        () ->
            handler.handle(new javax.security.auth.callback.Callback[] {new NameCallback("name")}));
  }

  private static SecretManager manager() {
    return new SecretManager() {
      public ResolvedSecret resolve(String reference, SecretRequestContext context) {
        return ResolvedSecret.of(CANARY.toCharArray());
      }

      public void verifyReady(String reference) {}
    };
  }

  private static SecretRequestContext context() {
    return new SecretRequestContext("soap-signing", "session", "test", true);
  }
}
