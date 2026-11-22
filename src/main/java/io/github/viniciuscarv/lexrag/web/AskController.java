package io.github.viniciuscarv.lexrag.web;

import io.github.viniciuscarv.lexrag.answer.Answer;
import io.github.viniciuscarv.lexrag.answer.Answerer;
import io.github.viniciuscarv.lexrag.retrieve.Mode;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AskController {

  record AskRequest(String question, String mode) {}

  private final Answerer answerer;

  public AskController(Answerer answerer) {
    this.answerer = answerer;
  }

  @PostMapping("/ask")
  Answer ask(@RequestBody AskRequest req) {
    if (req.question() == null || req.question().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question must not be blank");
    }
    return answerer.answer(req.question(), parseMode(req.mode()));
  }

  private static Mode parseMode(String s) {
    try {
      return s == null || s.isBlank() ? Mode.HYBRID : Mode.valueOf(s.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown mode: " + s);
    }
  }
}
