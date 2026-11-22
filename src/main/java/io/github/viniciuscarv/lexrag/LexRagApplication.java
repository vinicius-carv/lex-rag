package io.github.viniciuscarv.lexrag;

import io.github.viniciuscarv.lexrag.ingest.Ingest;
import java.util.Arrays;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LexRagApplication {

  public static void main(String[] args) {
    boolean ingest = Arrays.asList(args).contains("--ingest");
    if (ingest) {
      System.setProperty("spring.main.web-application-type", "none");
      System.setProperty("lexrag.ingest", "true");
    }
    if (Arrays.asList(args).contains("--record")) {
      System.setProperty("lexrag.record", "true");
    }
    ConfigurableApplicationContext ctx = SpringApplication.run(LexRagApplication.class, args);
    if (ingest) {
      System.exit(SpringApplication.exit(ctx));
    }
  }

  @Bean
  @ConditionalOnProperty("lexrag.ingest")
  ApplicationRunner ingestRunner(Ingest ingest) {
    return args -> ingest.run();
  }
}
