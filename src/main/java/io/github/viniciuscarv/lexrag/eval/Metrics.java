package io.github.viniciuscarv.lexrag.eval;

import io.github.viniciuscarv.lexrag.answer.Answer;
import io.github.viniciuscarv.lexrag.answer.CitationStatus;
import io.github.viniciuscarv.lexrag.answer.VerifiedCitation;
import io.github.viniciuscarv.lexrag.retrieve.Mode;
import java.util.List;
import java.util.Map;

public final class Metrics {

  private Metrics() {}

  public record RetrievalRow(Mode mode, double hit1, double recall5, double mrr10, int n) {}

  public record AnswerRow(
      String provider,
      Mode mode,
      double citationPrecision,
      double supportedRate,
      double answerRecall,
      double abstention,
      double falseAbstention,
      int failed,
      int n,
      Double judgeSupported) {}

  public static double hitAt1(List<String> ranked, List<String> gold) {
    return !ranked.isEmpty() && gold.contains(ranked.get(0)) ? 1 : 0;
  }

  public static double recallAt(List<String> ranked, List<String> gold, int k) {
    if (gold.isEmpty()) return 0;
    long hits = gold.stream().filter(ranked.stream().limit(k).toList()::contains).count();
    return (double) hits / gold.size();
  }

  public static double reciprocalRank(List<String> ranked, List<String> gold, int k) {
    for (int i = 0; i < Math.min(k, ranked.size()); i++) {
      if (gold.contains(ranked.get(i))) return 1.0 / (i + 1);
    }
    return 0;
  }

  /** {@code answers} maps question id to its answer; denominators follow the spec's Metrics section. */
  public static AnswerRow aggregate(
      String provider, Mode mode, List<Question> questions, Map<String, Answer> answers, Double judge) {
    int cites = 0, supported = 0, precise = 0, answerable = 0, recalled = 0, falseAbst = 0;
    int unanswerable = 0, abst = 0, failed = 0;
    for (Question q : questions) {
      Answer a = answers.get(q.id());
      boolean isAnswerable = !q.type().equals("unanswerable");
      if (isAnswerable) answerable++;
      else unanswerable++;
      if (a.failed()) {
        failed++;
        continue;
      }
      if (a.insufficient()) {
        if (isAnswerable) falseAbst++;
        else abst++;
      }
      boolean goldHit = false;
      for (VerifiedCitation c : a.citations()) {
        cites++;
        boolean ok = c.status() != CitationStatus.UNSUPPORTED;
        boolean inGold = q.gold().contains(c.articleId());
        if (ok) supported++;
        if (ok && inGold) precise++;
        goldHit |= ok && inGold;
      }
      if (isAnswerable && goldHit && !a.insufficient()) recalled++;
    }
    return new AnswerRow(
        provider, mode, ratio(precise, cites), ratio(supported, cites), ratio(recalled, answerable),
        ratio(abst, unanswerable), ratio(falseAbst, answerable), failed, questions.size(), judge);
  }

  private static double ratio(int num, int den) {
    return den == 0 ? 0 : (double) num / den;
  }
}
