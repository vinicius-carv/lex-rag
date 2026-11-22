package io.github.viniciuscarv.lexrag.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.viniciuscarv.lexrag.answer.Answer;
import io.github.viniciuscarv.lexrag.answer.CitationStatus;
import io.github.viniciuscarv.lexrag.answer.VerifiedCitation;
import io.github.viniciuscarv.lexrag.retrieve.Mode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricsTest {

  @Test
  void retrievalMetrics() {
    assertThat(Metrics.hitAt1(List.of("a", "b"), List.of("a"))).isEqualTo(1);
    assertThat(Metrics.hitAt1(List.of("b", "a"), List.of("a"))).isEqualTo(0);
    assertThat(Metrics.recallAt(List.of("a", "b", "c"), List.of("a", "c", "z"), 5)).isCloseTo(2.0 / 3, within(1e-9));
    assertThat(Metrics.recallAt(List.of("a", "b", "c"), List.of("c"), 2)).isEqualTo(0);
    assertThat(Metrics.reciprocalRank(List.of("x", "y", "a"), List.of("a"), 10)).isCloseTo(1.0 / 3, within(1e-9));
    assertThat(Metrics.reciprocalRank(List.of("x", "y"), List.of("a"), 10)).isEqualTo(0);
  }

  private static VerifiedCitation cite(String id, CitationStatus s) {
    return new VerifiedCitation(id, id, "q", s);
  }

  private static Answer ans(boolean insufficient, boolean failed, VerifiedCitation... cs) {
    return new Answer("q", Mode.HYBRID, "ollama", "", insufficient, failed, List.of(cs), List.of());
  }

  @Test
  void answerAggregation() {
    var qs =
        List.of(
            new Question("q1", "?", List.of("g:art1"), "single", ""),
            new Question("q2", "?", List.of("g:art2"), "single", ""),
            new Question("q3", "?", List.of("g:art3"), "single", ""),
            new Question("q4", "?", List.of(), "unanswerable", ""),
            new Question("q5", "?", List.of("g:art5"), "single", ""));
    var answers =
        Map.of(
            "q1", ans(false, false, cite("g:art1", CitationStatus.VERIFIED), cite("g:art9", CitationStatus.UNSUPPORTED)),
            "q2", ans(false, false, new VerifiedCitation("g:art2", "g:art8", "q", CitationStatus.WRONG_ARTICLE)),
            "q3", ans(false, true),
            "q4", ans(true, false),
            "q5", ans(true, false));
    var r = Metrics.aggregate("ollama", Mode.HYBRID, qs, answers, null);
    assertThat(r.citationPrecision()).isCloseTo(2.0 / 3, within(1e-9));
    assertThat(r.supportedRate()).isCloseTo(2.0 / 3, within(1e-9));
    assertThat(r.answerRecall()).isEqualTo(0.5);
    assertThat(r.abstention()).isEqualTo(1.0);
    assertThat(r.falseAbstention()).isEqualTo(0.25);
    assertThat(r.failed()).isEqualTo(1);
    assertThat(r.n()).isEqualTo(5);
    assertThat(r.judgeSupported()).isNull();
  }
}
