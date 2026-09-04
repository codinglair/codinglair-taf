package com.codinglair.taf.demo.sauce.configuration;

import com.codinglair.taf.demo.sauce.component.HeaderComponent;
import com.codinglair.taf.demo.sauce.page.CartPage;
import com.codinglair.taf.demo.sauce.page.CheckoutPage;
import com.codinglair.taf.demo.sauce.page.LoginPage;
import com.codinglair.taf.demo.sauce.page.ProductsPage;
import com.codinglair.taf.demo.sauce.service.LoginWorkflow;
import com.codinglair.taf.demo.sauce.validation.ProductValidator;
import com.codinglair.taf.demo.sauce.validation.StringValidator;
import com.codinglair.taf.runtime.definition.CsvTestDefinitionConfiguration;
import com.codinglair.taf.runtime.definition.CsvTestDefinitionRepository;
import com.codinglair.taf.runtime.definition.DefinitionResourceLocation;
import com.codinglair.taf.runtime.definition.SecretFieldDefinition;
import com.codinglair.taf.runtime.definition.SecretKind;
import com.codinglair.taf.runtime.definition.TestDefinitionRepository;
import com.codinglair.taf.runtime.definition.TestDefinitionResolver;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.web.playwright.PlaywrightObjectFactory;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ConsumerEnvironmentProperties.class, SauceDemoProperties.class})
public class ConsumerConfiguration {
  @Bean
  @ConditionalOnProperty(
      prefix = "taf.demo",
      name = "test-definition-provider",
      havingValue = "file-csv")
  TestDefinitionResolver definitions(SauceDemoProperties properties) {
    DefinitionResourceLocation inputs = definition(properties.testDataLocation(), "inputs.csv");
    DefinitionResourceLocation expected =
        definition(properties.testDataLocation(), "expected-outputs.csv");
    return new TestDefinitionResolver(
        new CsvTestDefinitionRepository(
            new CsvTestDefinitionConfiguration(
                inputs,
                expected,
                "caseId",
                Map.of("passwordReference", SecretFieldDefinition.required(SecretKind.PASSWORD)),
                Map.of())));
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "taf.demo",
      name = "test-definition-provider",
      havingValue = "mongodb")
  TestDefinitionResolver mongoDefinitions(TestDefinitionRepository repository) {
    if (!(repository
        instanceof com.codinglair.taf.runtime.definition.mongodb.MongoTestDefinitionRepository)) {
      throw new IllegalStateException(
          "Mongo mode requires MongoTestDefinitionRepository with no file fallback");
    }
    return new TestDefinitionResolver(repository);
  }

  @Bean
  LoginPage loginPage(PlaywrightObjectFactory factory) {
    return new LoginPage(factory);
  }

  @Bean
  ProductsPage productsPage(PlaywrightObjectFactory factory) {
    return new ProductsPage(factory);
  }

  @Bean
  LoginWorkflow loginWorkflow(LoginPage page, SecretManager secrets) {
    return new LoginWorkflow(page, secrets);
  }

  @Bean
  ProductValidator productValidator() {
    return new ProductValidator();
  }

  @Bean
  CartPage cartPage(PlaywrightObjectFactory factory) {
    return new CartPage(factory);
  }

  @Bean
  CheckoutPage checkoutPage(PlaywrightObjectFactory factory) {
    return new CheckoutPage(factory);
  }

  @Bean
  HeaderComponent headerComponent(PlaywrightObjectFactory factory) {
    return new HeaderComponent(factory);
  }

  @Bean
  StringValidator stringValidator() {
    return new StringValidator();
  }

  private static DefinitionResourceLocation definition(String location, String filename) {
    if (location == null || location.isBlank() || location.equals("REPLACE_ME")) {
      throw new IllegalArgumentException("Test-data location is unresolved");
    }
    if (location.startsWith("classpath:")) {
      String directory = location.substring("classpath:".length()).replace('\\', '/');
      return DefinitionResourceLocation.classpath(directory + "/" + filename);
    }
    return DefinitionResourceLocation.file(Path.of(location).resolve(filename));
  }
}
