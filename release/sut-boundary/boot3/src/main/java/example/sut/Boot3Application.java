package example.sut;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Boot3Application {
  public static void main(String[] args) {
    SpringApplication.run(Boot3Application.class, args);
  }

  @GetMapping("/qualification")
  String qualification() {
    return "boot3-ok";
  }
}
