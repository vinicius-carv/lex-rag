package io.github.viniciuscarv.lexrag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LexRagApplicationTest {

  @Test
  void askIsNonWebAndDoesNotIngest() {
    var p = LexRagApplication.properties(List.of("--ask", "prazo?", "--mode", "dense", "--provider", "anthropic"));
    assertThat(p)
        .containsEntry("lexrag.ask", "prazo?")
        .containsEntry("lexrag.ask-mode", "dense")
        .containsEntry("lexrag.chat.provider", "anthropic")
        .containsEntry("spring.main.web-application-type", "none")
        .doesNotContainKey("lexrag.ingest");
  }

  @Test
  void ingestIngests() {
    assertThat(LexRagApplication.properties(List.of("--ingest", "--record")))
        .containsEntry("lexrag.ingest", "true")
        .containsEntry("lexrag.record", "true")
        .doesNotContainKey("lexrag.ask");
  }

  @Test
  void noFlagsStartsWebServer() {
    assertThat(LexRagApplication.properties(List.of())).isEmpty();
  }
}
