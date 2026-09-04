package com.codinglair.taf.demo.sauce.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codinglair.taf.demo.sauce.SauceDemoApplication;
import com.codinglair.taf.demo.sauce.configuration.ConsumerEnvironmentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("ci")
@SpringBootTest(classes = SauceDemoApplication.class)
class CiContextTest {
  @Autowired ConsumerEnvironmentProperties environment;

  @org.junit.jupiter.api.Test
  void intentionalCiOverridesBind() {
    assertEquals("ci", environment.name());
  }
}
