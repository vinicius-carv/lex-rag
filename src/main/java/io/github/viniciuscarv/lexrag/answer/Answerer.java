package io.github.viniciuscarv.lexrag.answer;

import io.github.viniciuscarv.lexrag.answer.AnswerParser.ParsedAnswer;
import io.github.viniciuscarv.lexrag.record.RecordingChat;
import io.github.viniciuscarv.lexrag.retrieve.Mode;
import io.github.viniciuscarv.lexrag.retrieve.RetrievedArticle;
import io.github.viniciuscarv.lexrag.retrieve.Retriever;
import io.github.viniciuscarv.lexrag.store.ChunkRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

public class Answerer {

  private static final Logger log = LoggerFactory.getLogger(Answerer.class);

  static final int TOP_ARTICLES = 5;
  static final int PROMPT_BUDGET = 6000;
  static final String RETRY_SUFFIX = "\n\nResponda somente com JSON válido.";

  private final Retriever retriever;
  private final ChunkRepository repo;
  private final RecordingChat chat;
  private final String provider;
  private final String template;

  public Answerer(Retriever retriever, ChunkRepository repo, RecordingChat chat, String provider) {
    this.retriever = retriever;
    this.repo = repo;
    this.chat = chat;
    this.provider = provider;
    try {
      this.template =
          new ClassPathResource("prompts/answer-v1.txt").getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Answer answer(String question, Mode mode) {
    List<RetrievedArticle> articles = retriever.retrieve(question, mode, TOP_ARTICLES);
    List<String> ids = articles.stream().map(RetrievedArticle::articleId).toList();
    String prompt = prompt(question, articles);

    Optional<ParsedAnswer> parsed = tryComplete(question, prompt);
    if (parsed.isEmpty()) parsed = tryComplete(question, prompt + RETRY_SUFFIX);
    if (parsed.isEmpty()) {
      return new Answer(question, mode, provider, "", false, true, List.of(), ids);
    }

    ParsedAnswer p = parsed.get();
    List<Citation> cites = p.insufficient() ? List.<Citation>of() : p.citations().stream().limit(3).toList();
    var verified = CitationVerifier.verify(cites, repo.articleTexts(ids));
    return new Answer(question, mode, provider, p.answer(), p.insufficient(), false, verified, ids);
  }

  // A chat error (e.g. timeout) must not abort a whole eval run: treat it like malformed output.
  private Optional<ParsedAnswer> tryComplete(String question, String prompt) {
    try {
      return AnswerParser.parse(chat.complete(prompt));
    } catch (RuntimeException e) {
      log.warn("Chat call failed for question '{}': {}", question.substring(0, Math.min(60, question.length())), e.toString());
      return Optional.empty();
    }
  }

  private String prompt(String question, List<RetrievedArticle> articles) {
    StringBuilder sb = new StringBuilder();
    int left = PROMPT_BUDGET;
    for (RetrievedArticle a : articles) {
      sb.append('[').append(a.articleId()).append("]\n");
      for (String piece : a.pieces()) {
        if (left <= 0) break;
        String t = piece.length() > left ? piece.substring(0, left) : piece;
        sb.append(t).append('\n');
        left -= t.length();
      }
      sb.append('\n');
    }
    return template.replace("{{question}}", question).replace("{{articles}}", sb.toString().strip());
  }
}
