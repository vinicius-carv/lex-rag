package io.github.viniciuscarv.lexrag.answer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnswerParserTest {

  private static final String JSON =
      "{\"answer\":\"Sete dias.\",\"citations\":[{\"articleId\":\"cdc:art49\",\"quote\":\"sete dias\"}],\"insufficient\":false}";

  @Test
  void plainJson() {
    var p = AnswerParser.parse(JSON).orElseThrow();
    assertThat(p.answer()).isEqualTo("Sete dias.");
    assertThat(p.citations()).containsExactly(new Citation("cdc:art49", "sete dias"));
    assertThat(p.insufficient()).isFalse();
  }

  @Test
  void fencedJson() {
    assertThat(AnswerParser.parse("```json\n" + JSON + "\n```")).isPresent();
  }

  @Test
  void proseThenJson() {
    assertThat(AnswerParser.parse("Claro! Aqui está: " + JSON + " Espero ter ajudado.")).isPresent();
  }

  @Test
  void missingCitationsIsEmptyList() {
    var p = AnswerParser.parse("{\"answer\":\"x\",\"insufficient\":true}").orElseThrow();
    assertThat(p.citations()).isEmpty();
    assertThat(p.insufficient()).isTrue();
  }

  @Test
  void garbage() {
    assertThat(AnswerParser.parse("não sei")).isEmpty();
    assertThat(AnswerParser.parse("{broken")).isEmpty();
    assertThat(AnswerParser.parse("{\"foo\":1}")).isEmpty();
  }
}
