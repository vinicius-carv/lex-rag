package io.github.viniciuscarv.lexrag.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.viniciuscarv.lexrag.answer.Answer;
import io.github.viniciuscarv.lexrag.answer.CitationStatus;
import io.github.viniciuscarv.lexrag.answer.VerifiedCitation;
import io.github.viniciuscarv.lexrag.eval.Metrics.AnswerRow;
import io.github.viniciuscarv.lexrag.eval.Metrics.RetrievalRow;
import io.github.viniciuscarv.lexrag.record.RecordingChat;
import io.github.viniciuscarv.lexrag.retrieve.Mode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalRunnerTest {

  @TempDir Path dir;

  private EvalReport report(double recall5, double supported) {
    return new EvalReport(
        List.of(new RetrievalRow(Mode.SYSTEM, 0, recall5, 0, 1)),
        List.of(new AnswerRow("ollama", Mode.SYSTEM, 0, supported, 0, 0, 0, 0, 1, null)),
        List.of());
  }

  @Test
  void gateToleratesTwoPointsAndFailsBeyond() throws Exception {
    Path b = dir.resolve("baseline.json");
    Files.writeString(b, "{\"systemRecall5\":0.80,\"supportedRate\":0.70}");
    assertThat(EvalRunner.gate(report(0.79, 0.69), b, true)).isTrue();
    assertThat(EvalRunner.gate(report(0.77, 0.69), b, true)).isFalse();
    assertThat(EvalRunner.gate(report(0.79, 0.67), b, true)).isFalse();
  }

  @Test
  void gateFailsOnMissingRowsButRetrievalOnlySkipsAnswers() throws Exception {
    Path b = dir.resolve("baseline.json");
    Files.writeString(b, "{\"systemRecall5\":0.80,\"supportedRate\":0.70}");
    var retrievalOnly = new EvalReport(report(0.80, 0).retrieval(), List.of(), List.of());
    assertThat(EvalRunner.gate(retrievalOnly, b, false)).isTrue();
    assertThat(EvalRunner.gate(retrievalOnly, b, true)).isFalse();
    var noSystem = new EvalReport(List.of(new RetrievalRow(Mode.KEYWORD, 0, 1, 0, 1)), List.of(), List.of());
    assertThat(EvalRunner.gate(noSystem, b, false)).isFalse();
  }

  @Test
  void judgeScoreIsShareOfSupportedAnswers() {
    RecordingChat chat = mock(RecordingChat.class);
    when(chat.complete(anyString())).thenReturn("{\"supported\":true}", "{\"supported\":false}");
    var cite = new VerifiedCitation("g:art1", "g:art1", "trecho", CitationStatus.VERIFIED);
    Answer ok = new Answer("q", Mode.SYSTEM, "ollama", "a", false, false, List.of(cite), List.of());
    Answer abstained = new Answer("q", Mode.SYSTEM, "ollama", "", true, false, List.of(), List.of());
    Answer failed = new Answer("q", Mode.SYSTEM, "ollama", "", false, true, List.of(), List.of());
    var runner = new EvalRunner(null, null, chat, List.of());
    assertThat(runner.judgeSupported(List.of(ok, abstained, failed, ok))).isEqualTo(0.5);
  }
}
