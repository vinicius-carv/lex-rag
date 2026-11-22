package io.github.viniciuscarv.lexrag.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.viniciuscarv.lexrag.law.Article;
import io.github.viniciuscarv.lexrag.law.LawParser;
import java.io.IOException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class QuestionSetTest {

  private static final Path QUESTIONS = Path.of("corpus/eval/questions.jsonl");
  private static final Path MANIFEST = Path.of("corpus/laws/manifest.json");
  private static final int RUN = 5;

  private static List<Question> questions;
  private static Map<String, Article> articles;

  @BeforeAll
  static void load() throws IOException {
    questions = Question.load(QUESTIONS);
    articles = LawParser.load(MANIFEST).stream().collect(Collectors.toMap(Article::id, Function.identity()));
  }

  @Test
  void sizeAndIdsAndTypes() {
    assertThat(questions).hasSizeBetween(55, 65);
    assertThat(questions.stream().map(Question::id)).doesNotHaveDuplicates();
    assertThat(questions).allSatisfy(q -> assertThat(q.type()).isIn("single", "multi", "unanswerable"));
    assertThat(questions).filteredOn(q -> q.type().equals("unanswerable"))
        .hasSizeBetween(4, 6)
        .allSatisfy(q -> assertThat(q.gold()).isEmpty());
    assertThat(questions).filteredOn(q -> !q.type().equals("unanswerable"))
        .allSatisfy(q -> assertThat(q.gold()).isNotEmpty());
    assertThat(questions).filteredOn(q -> q.type().equals("multi"))
        .allSatisfy(q -> assertThat(q.gold()).hasSizeGreaterThanOrEqualTo(2));
  }

  @Test
  void goldExistsAndIsNotRevoked() {
    for (Question q : questions) {
      for (String id : q.gold()) {
        assertThat(articles).as("%s gold %s", q.id(), id).containsKey(id);
        assertThat(articles.get(id).revoked()).as("%s gold %s revoked", q.id(), id).isFalse();
      }
    }
  }

  @Test
  void coversBothLaws() {
    assertThat(questions.stream().filter(q -> q.gold().stream().anyMatch(g -> g.startsWith("cdc:"))).count())
        .isGreaterThanOrEqualTo(25);
    assertThat(questions.stream().filter(q -> q.gold().stream().anyMatch(g -> g.startsWith("cf88:"))).count())
        .isGreaterThanOrEqualTo(25);
  }

  @Test
  void questionsAreNotCopiedFromGoldText() {
    for (Question q : questions) {
      Set<String> grams = grams(tokens(q.question()));
      for (String id : q.gold()) {
        Set<String> shared = new HashSet<>(grams);
        shared.retainAll(grams(tokens(articles.get(id).text())));
        assertThat(shared).as("%s shares a %d-token run with %s", q.id(), RUN, id).isEmpty();
      }
    }
  }

  private static List<String> tokens(String text) {
    String plain = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return java.util.Arrays.stream(plain.split("[^\\p{L}\\p{Nd}]+")).filter(t -> !t.isEmpty()).toList();
  }

  private static Set<String> grams(List<String> tokens) {
    Set<String> out = new HashSet<>();
    for (int i = 0; i + RUN <= tokens.size(); i++) {
      out.add(String.join(" ", new ArrayList<>(tokens.subList(i, i + RUN))));
    }
    return out;
  }
}
