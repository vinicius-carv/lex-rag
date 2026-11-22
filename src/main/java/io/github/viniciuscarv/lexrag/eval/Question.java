package io.github.viniciuscarv.lexrag.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** One hand-written evaluation question; {@code gold} is empty for unanswerable questions. */
public record Question(String id, String question, List<String> gold, String type, String note) {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  public static List<Question> load(Path jsonl) {
    try {
      return Files.readAllLines(jsonl).stream()
          .filter(line -> !line.isBlank())
          .map(line -> MAPPER.readValue(line, Question.class))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
