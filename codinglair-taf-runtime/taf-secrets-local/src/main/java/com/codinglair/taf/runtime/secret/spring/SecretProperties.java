package com.codinglair.taf.runtime.secret.spring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("taf.secrets")
@Validated
public class SecretProperties {
  private String provider;
  private String jasyptMasterKeyEnvironmentVariable = "TAF_JASYPT_MASTER_KEY";
  private final List<String> references = new ArrayList<>();
  private final Map<String, String> routing = new LinkedHashMap<>();

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getJasyptMasterKeyEnvironmentVariable() {
    return jasyptMasterKeyEnvironmentVariable;
  }

  public void setJasyptMasterKeyEnvironmentVariable(String value) {
    this.jasyptMasterKeyEnvironmentVariable = value;
  }

  public List<String> getReferences() {
    return references;
  }

  public Map<String, String> getRouting() {
    return routing;
  }
}
