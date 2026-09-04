package com.codinglair.taf.api.soap;

import java.util.HashMap;
import java.util.Map;
import javax.security.auth.callback.CallbackHandler;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.ConfigurationConstants;

/** Controlled CXF escape hatch for generated-client WS-Security configuration. */
public final class CxfWsSecurity {
  private CxfWsSecurity() {}

  /**
   * Adds an outbound X.509 XML-signature interceptor. The crypto properties resource must contain
   * provider/keystore metadata but no plaintext password; passwords are supplied by the callback.
   */
  public static void configureSignature(
      Object generatedPort,
      String signingAlias,
      String cryptoPropertiesResource,
      CallbackHandler passwordCallback) {
    requireSafe(signingAlias, "signingAlias");
    requireSafe(cryptoPropertiesResource, "cryptoPropertiesResource");
    if (passwordCallback == null)
      throw new IllegalArgumentException("passwordCallback is required");
    Map<String, Object> properties = new HashMap<>();
    properties.put(ConfigurationConstants.ACTION, ConfigurationConstants.SIGNATURE);
    properties.put(ConfigurationConstants.USER, signingAlias);
    properties.put(ConfigurationConstants.SIGNATURE_USER, signingAlias);
    properties.put(ConfigurationConstants.SIG_PROP_FILE, cryptoPropertiesResource);
    properties.put(ConfigurationConstants.PW_CALLBACK_REF, passwordCallback);
    ClientProxy.getClient(generatedPort)
        .getOutInterceptors()
        .add(new WSS4JOutInterceptor(properties));
  }

  private static void requireSafe(String value, String name) {
    if (value == null || value.isBlank() || value.contains("\n") || value.contains("\r")) {
      throw new IllegalArgumentException(name + " is invalid");
    }
  }
}
