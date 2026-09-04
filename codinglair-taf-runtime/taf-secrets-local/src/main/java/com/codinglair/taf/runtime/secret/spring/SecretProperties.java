package com.codinglair.taf.runtime.secret.spring;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("taf.secrets")
public class SecretProperties {
  private String jasyptMasterKeyEnvironmentVariable = "TAF_JASYPT_MASTER_KEY";
  private final List<String> references = new ArrayList<>();

  public String getJasyptMasterKeyEnvironmentVariable() {
    return jasyptMasterKeyEnvironmentVariable;
  }

  public void setJasyptMasterKeyEnvironmentVariable(String value) {
    this.jasyptMasterKeyEnvironmentVariable = value;
  }

  public List<String> getReferences() {
    return references;
  }
}
