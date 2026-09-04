package com.codinglair.taf.mcp.stdio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Local STDIO launcher; deployments provide the shared MCP service beans. */
@SpringBootApplication(proxyBeanMethods = false)
public final class TafMcpStdioApplication {
  private TafMcpStdioApplication() {}

  public static void main(String[] args) {
    StdioProtocolInput.install();
    SpringApplication.run(TafMcpStdioApplication.class, args);
  }
}
