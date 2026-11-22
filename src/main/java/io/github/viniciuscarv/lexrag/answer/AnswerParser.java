package io.github.viniciuscarv.lexrag.answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Lenient parse of the model's JSON: tolerates code fences and prose around the object. */
public final class AnswerParser {

  public record ParsedAnswer(String answer, List<Citation> citations, boolean insufficient) {}

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private AnswerParser() {}

  public static Optional<ParsedAnswer> parse(String raw) {
    if (raw == null) return Optional.empty();
    int a = raw.indexOf('{');
    int b = raw.lastIndexOf('}');
    if (a < 0 || b < a) return Optional.empty();
    try {
      JsonNode n = MAPPER.readTree(raw.substring(a, b + 1));
      if (!n.isObject() || !n.hasNonNull("answer")) return Optional.empty();
      List<Citation> cites = new ArrayList<>();
      for (JsonNode c : n.path("citations")) {
        cites.add(new Citation(c.path("articleId").asString(""), c.path("quote").asString("")));
      }
      return Optional.of(
          new ParsedAnswer(
              n.get("answer").asString(), cites, n.path("insufficient").asBoolean(false)));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }
}
