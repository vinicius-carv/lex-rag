package io.github.viniciuscarv.lexrag.eval;

import io.github.viniciuscarv.lexrag.answer.Answer;
import io.github.viniciuscarv.lexrag.answer.Answerer;
import io.github.viniciuscarv.lexrag.answer.VerifiedCitation;
import io.github.viniciuscarv.lexrag.eval.Metrics.AnswerRow;
import io.github.viniciuscarv.lexrag.eval.Metrics.RetrievalRow;
import io.github.viniciuscarv.lexrag.record.RecordingChat;
import io.github.viniciuscarv.lexrag.retrieve.Mode;
import io.github.viniciuscarv.lexrag.retrieve.RetrievedArticle;
import io.github.viniciuscarv.lexrag.retrieve.Retriever;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class EvalRunner {

  static final double TOLERANCE = 0.02;
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final Retriever retriever;
  private final Answerer answerer;
  private final RecordingChat judge;
  private final List<Question> questions;

  /** {@code answerer} may be null for retrieval-only runs and {@code judge} when no judge is wanted. */
  public EvalRunner(Retriever retriever, Answerer answerer, RecordingChat judge, List<Question> questions) {
    this.retriever = retriever;
    this.answerer = answerer;
    this.judge = judge;
    this.questions = questions;
  }

  /** Retrieval rows for every mode; answer rows only for Mode.SYSTEM and KEYWORD (the generation baseline). */
  public EvalReport run(Set<Mode> modes, boolean answers) {
    Map<String, Map<String, Object>> perQuestion = new LinkedHashMap<>();
    for (Question q : questions) {
      var m = new LinkedHashMap<String, Object>();
      m.put("id", q.id());
      m.put("type", q.type());
      m.put("gold", q.gold());
      m.put("retrieved", new LinkedHashMap<String, Object>());
      m.put("answers", new LinkedHashMap<String, Object>());
      perQuestion.put(q.id(), m);
    }
    List<RetrievalRow> retrieval = new ArrayList<>();
    List<Question> answerable = questions.stream().filter(q -> !q.type().equals("unanswerable")).toList();
    for (Mode mode : EnumSet.copyOf(modes)) {
      double hit = 0, rec = 0, mrr = 0;
      for (Question q : answerable) {
        List<String> ids =
            retriever.retrieve(q.question(), mode, 10).stream().map(RetrievedArticle::articleId).toList();
        hit += Metrics.hitAt1(ids, q.gold());
        rec += Metrics.recallAt(ids, q.gold(), 5);
        mrr += Metrics.reciprocalRank(ids, q.gold(), 10);
        nested(perQuestion.get(q.id()), "retrieved").put(mode.name(), ids);
      }
      int n = answerable.size();
      retrieval.add(new RetrievalRow(mode, hit / n, rec / n, mrr / n, n));
    }
    List<AnswerRow> rows = new ArrayList<>();
    if (answers) {
      for (Mode mode : EnumSet.copyOf(modes)) {
        if (mode != Mode.SYSTEM && mode != Mode.KEYWORD) continue;
        Map<String, Answer> byId = new LinkedHashMap<>();
        for (Question q : questions) {
          Answer a = answerer.answer(q.question(), mode);
          byId.put(q.id(), a);
          nested(perQuestion.get(q.id()), "answers").put(mode.name(), a);
        }
        String provider = byId.values().iterator().next().provider();
        Double judged = mode == Mode.SYSTEM && judge != null ? judgeSupported(byId.values()) : null;
        rows.add(Metrics.aggregate(provider, mode, questions, byId, judged));
      }
    }
    return new EvalReport(retrieval, rows, new ArrayList<>(perQuestion.values()));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> nested(Map<String, Object> m, String key) {
    return (Map<String, Object>) m.get(key);
  }

  /** Share of non-failed, non-abstained answers the judge calls fully supported; unparseable verdicts count as no. */
  Double judgeSupported(Iterable<Answer> answers) {
    int n = 0, yes = 0;
    for (Answer a : answers) {
      if (a.failed() || a.insufficient()) continue;
      n++;
      String quotes =
          a.citations().stream().map(VerifiedCitation::quote).collect(Collectors.joining("\n- ", "- ", ""));
      String prompt =
          "Todas as afirmações da resposta são sustentadas pelos trechos citados? "
              + "Responda JSON {\"supported\": bool}\n\nResposta:\n"
              + a.answer()
              + "\n\nTrechos citados:\n"
              + quotes;
      if (supported(judge.complete(prompt))) yes++;
    }
    return n == 0 ? null : (double) yes / n;
  }

  private static boolean supported(String json) {
    try {
      return MAPPER.readTree(json).path("supported").asBoolean(false);
    } catch (RuntimeException e) {
      return false;
    }
  }

  /**
   * Fails when system-mode Recall@5 or (if answers were run) the ollama/system-mode supported rate fall more than 0.02
   * below the baseline, or when a row to gate is missing.
   */
  public static boolean gate(EvalReport r, Path baseline, boolean answersRun) {
    JsonNode b;
    try {
      b = MAPPER.readTree(Files.readString(baseline));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    boolean ok = true;
    String sys = Mode.SYSTEM.name().toLowerCase(Locale.ROOT);
    if (r.retrieval().stream().noneMatch(x -> x.mode() == Mode.SYSTEM)) {
      System.out.printf("GATE FAIL: no %s retrieval row to gate; run with --mode %s or all%n", sys, sys);
      ok = false;
    }
    if (answersRun && r.answers().stream().noneMatch(x -> x.mode() == Mode.SYSTEM && x.provider().equals("ollama"))) {
      System.out.printf("GATE FAIL: no ollama/%s answer row to gate; use --provider ollama or --retrieval-only%n", sys);
      ok = false;
    }
    double minRecall = b.get("systemRecall5").asDouble() - TOLERANCE;
    for (RetrievalRow row : r.retrieval()) {
      if (row.mode() == Mode.SYSTEM && row.recall5() < minRecall - 1e-9) {
        System.out.printf("GATE FAIL: %s recall@5 %.3f < %.3f%n", sys, row.recall5(), minRecall);
        ok = false;
      }
    }
    double minSupported = b.get("supportedRate").asDouble() - TOLERANCE;
    for (AnswerRow row : r.answers()) {
      if (row.mode() == Mode.SYSTEM && row.provider().equals("ollama") && row.supportedRate() < minSupported - 1e-9) {
        System.out.printf("GATE FAIL: ollama/%s supported rate %.3f < %.3f%n", sys, row.supportedRate(), minSupported);
        ok = false;
      }
    }
    return ok;
  }
}
