package io.github.viniciuscarv.lexrag;

import io.github.viniciuscarv.lexrag.answer.Answer;
import io.github.viniciuscarv.lexrag.answer.Answerer;
import io.github.viniciuscarv.lexrag.ingest.Ingest;
import io.github.viniciuscarv.lexrag.retrieve.Mode;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    properties(a).forEach(System::setProperty);
    ConfigurableApplicationContext ctx = SpringApplication.run(LexRagApplication.class, args);
    if (a.contains("--ingest") || a.contains("--ask") || a.contains("--eval")) {
      System.exit(SpringApplication.exit(ctx));
    }
  }

  /** Maps CLI flags to system properties; --ask and --ingest are both non-web, only --ingest ingests. */
  static Map<String, String> properties(List<String> a) {
    Map<String, String> p = new LinkedHashMap<>();
    boolean ingest = a.contains("--ingest");
    boolean ask = a.contains("--ask");
    if (ask) {
      p.put("lexrag.ask", opt(a, "--ask"));
      p.put("lexrag.ask-mode", Objects.toString(opt(a, "--mode"), Mode.SYSTEM.name().toLowerCase(Locale.ROOT)));
    }
    boolean eval = a.contains("--eval");
    if (eval) {
      p.put("lexrag.eval", "true");
      p.put("lexrag.eval-mode", Objects.toString(opt(a, "--mode"), "all"));
      if (a.contains("--retrieval-only")) p.put("lexrag.eval-retrieval-only", "true");
      if (a.contains("--judge")) p.put("lexrag.judge", "true");
    }
    String provider = opt(a, "--provider");
    if (provider != null) p.put("lexrag.chat.provider", provider);
    if (ingest || ask || eval) p.put("spring.main.web-application-type", "none");
    if (ingest) p.put("lexrag.ingest", "true");
    if (ingest || eval) p.put("lexrag.live", "false");
    if (a.contains("--record")) p.put("lexrag.record", "true");
    return p;
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
