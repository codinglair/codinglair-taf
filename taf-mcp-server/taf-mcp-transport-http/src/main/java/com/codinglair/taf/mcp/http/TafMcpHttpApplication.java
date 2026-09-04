package com.codinglair.taf.mcp.http;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Standalone remote MCP control-plane launcher. */
@SpringBootApplication
public class TafMcpHttpApplication {
  public static void main(String[] args) {
    SpringApplication.run(TafMcpHttpApplication.class, args);
  }
}
