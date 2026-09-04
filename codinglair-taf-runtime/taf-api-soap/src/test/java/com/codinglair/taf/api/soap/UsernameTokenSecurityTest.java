package com.codinglair.taf.api.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.secret.ResolvedSecret;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WS-Security UsernameToken")
class UsernameTokenSecurityTest {
  @Test
  @DisplayName("resolves references only while securing and does not expose values in diagnostics")
  void resolvesTransiently() {
    AtomicInteger resolutions = new AtomicInteger();
    SecretManager manager =
        new SecretManager() {
          public ResolvedSecret resolve(String reference, SecretRequestContext context) {
            resolutions.incrementAndGet();
            return ResolvedSecret.of(
                (reference.endsWith("USER") ? "canary-user" : "canary-password").toCharArray());
          }

          public void verifyReady(String reference) {}
        };
    var security =
        new UsernameTokenSecurity(
            manager,
            "secret://env/USER",
            "secret://env/PASSWORD",
            new SecretRequestContext("soap", "session", "test", true));
    var document =
        SoapXml.parse(
            "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body/></s:Envelope>");
    security.secure(document);
    assertThat(resolutions).hasValue(2);
    assertThat(SoapXml.serialize(document)).contains("canary-user", "canary-password");
    assertThat(security.toString()).doesNotContain("USER", "PASSWORD", "canary");
  }
}
