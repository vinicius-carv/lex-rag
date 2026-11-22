package io.github.viniciuscarv.lexrag.eval;

import io.github.viniciuscarv.lexrag.eval.Metrics.AnswerRow;
import io.github.viniciuscarv.lexrag.eval.Metrics.RetrievalRow;
import java.util.List;
import java.util.Map;

public record EvalReport(
    List<RetrievalRow> retrieval, List<AnswerRow> answers, List<Map<String, Object>> perQuestion) {

  public String toMarkdown() {
    StringBuilder sb = new StringBuilder("| Mode | Hit@1 | Recall@5 | MRR@10 | n |\n|---|---|---|---|---|\n");
    for (RetrievalRow r : retrieval) {
      sb.append(String.format("| %s | %.3f | %.3f | %.3f | %d |%n", r.mode(), r.hit1(), r.recall5(), r.mrr10(), r.n()));
    }
    if (answers.isEmpty()) return sb.toString();
    boolean judged = answers.stream().anyMatch(a -> a.judgeSupported() != null);
    sb.append("\n| Provider | Mode | Citation precision | Supported rate | Answer recall | Abstention | False abstention | Failed | n |");
    sb.append(judged ? " Judge supported |\n|---|---|---|---|---|---|---|---|---|---|\n" : "\n|---|---|---|---|---|---|---|---|---|\n");
    for (AnswerRow a : answers) {
      sb.append(
          String.format(
              "| %s | %s | %.3f | %.3f | %.3f | %.3f | %.3f | %d | %d |",
              a.provider(), a.mode(), a.citationPrecision(), a.supportedRate(), a.answerRecall(),
              a.abstention(), a.falseAbstention(), a.failed(), a.n()));
      if (judged) sb.append(a.judgeSupported() == null ? " - |" : String.format(" %.3f |", a.judgeSupported()));
      sb.append('\n');
    }
    return sb.toString();
  }
}
