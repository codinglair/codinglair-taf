package com.codinglair.taf.demo.sauce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SauceDemoApplication {
  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(SauceDemoApplication.class);
    app.setWebApplicationType(WebApplicationType.NONE);
    app.run(args);
  }
}
