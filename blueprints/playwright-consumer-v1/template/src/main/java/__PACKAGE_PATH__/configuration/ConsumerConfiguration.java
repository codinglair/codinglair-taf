package __BASE_PACKAGE__.configuration;

import com.codinglair.taf.runtime.definition.CsvTestDefinitionConfiguration;
import com.codinglair.taf.runtime.definition.CsvTestDefinitionRepository;
import com.codinglair.taf.runtime.definition.DefinitionResourceLocation;
import com.codinglair.taf.runtime.definition.TestDefinitionResolver;
import com.codinglair.taf.web.playwright.PlaywrightObjectFactory;
import __BASE_PACKAGE__.page.LoginPage;
import __BASE_PACKAGE__.page.ProductsPage;
import __BASE_PACKAGE__.service.LoginWorkflow;
import __BASE_PACKAGE__.validation.ProductValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ConsumerEnvironmentProperties.class)
public class ConsumerConfiguration {
  @Bean
  TestDefinitionResolver definitions() {
    return new TestDefinitionResolver(
        new CsvTestDefinitionRepository(
            new CsvTestDefinitionConfiguration(
                DefinitionResourceLocation.classpath("test-data/inputs.csv"),
                DefinitionResourceLocation.classpath("test-data/expected-outputs.csv"),
                "caseId")));
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
  LoginWorkflow loginWorkflow(LoginPage page) {
    return new LoginWorkflow(page);
  }

  @Bean
  ProductValidator productValidator() {
    return new ProductValidator();
  }
}
