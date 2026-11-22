package io.github.viniciuscarv.lexrag;

import io.github.viniciuscarv.lexrag.answer.Answer;
import io.github.viniciuscarv.lexrag.answer.Answerer;
import io.github.viniciuscarv.lexrag.ingest.Ingest;
import io.github.viniciuscarv.lexrag.retrieve.Mode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LexRagApplication {

  public static void main(String[] args) {
    List<String> a = Arrays.asList(args);
    boolean ingest = a.contains("--ingest");
    int ask = a.indexOf("--ask");
    if (ask >= 0) {
      System.setProperty("lexrag.ask", opt(a, "--ask"));
      System.setProperty("lexrag.ask-mode", Objects.toString(opt(a, "--mode"), "hybrid"));
    }
    String provider = opt(a, "--provider");
    if (provider != null) System.setProperty("lexrag.chat.provider", provider);
    if (ingest || ask >= 0) {
      System.setProperty("spring.main.web-application-type", "none");
      System.setProperty("lexrag.ingest", "true");
    }
    if (Arrays.asList(args).contains("--record")) {
      System.setProperty("lexrag.record", "true");
    }
    ConfigurableApplicationContext ctx = SpringApplication.run(LexRagApplication.class, args);
    if (ingest || ask >= 0) {
      System.exit(SpringApplication.exit(ctx));
    }
  }

  private static String opt(List<String> args, String name) {
    int i = args.indexOf(name);
    if (i < 0 || i + 1 >= args.size()) {
      if (name.equals("--ask")) throw new IllegalArgumentException("--ask requires a question");
      return null;
    }
    return args.get(i + 1);
  }

  @Bean
  @ConditionalOnProperty("lexrag.ask")
  ApplicationRunner askRunner(
      Answerer answerer, @Value("${lexrag.ask}") String question, @Value("${lexrag.ask-mode}") String mode) {
    return args -> {
      Answer r = answerer.answer(question, Mode.valueOf(mode.toUpperCase(Locale.ROOT)));
      System.out.println(r.failed() ? "[answer failed: model returned no valid JSON]" : r.answer());
      if (r.insufficient()) System.out.println("[insufficient context]");
      r.citations().forEach(c -> System.out.println("- " + c.status() + " " + c.articleId() + ": " + c.quote()));
      System.out.println("retrieved: " + r.retrievedIds());
    };
  }

  @Bean
  @ConditionalOnProperty("lexrag.ingest")
  ApplicationRunner ingestRunner(Ingest ingest) {
    return args -> ingest.run();
  }
}
