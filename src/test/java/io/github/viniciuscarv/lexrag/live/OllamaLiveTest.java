package io.github.viniciuscarv.lexrag.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.viniciuscarv.lexrag.answer.Answer;
import io.github.viniciuscarv.lexrag.answer.Answerer;
import io.github.viniciuscarv.lexrag.config.ModelConfig;
import io.github.viniciuscarv.lexrag.law.LawParser;
import io.github.viniciuscarv.lexrag.record.RecordingChat;
import io.github.viniciuscarv.lexrag.record.Recordings;
import io.github.viniciuscarv.lexrag.retrieve.Mode;
import io.github.viniciuscarv.lexrag.retrieve.RetrievedArticle;
import io.github.viniciuscarv.lexrag.retrieve.Retriever;
import io.github.viniciuscarv.lexrag.store.ChunkRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("live")
class OllamaLiveTest {

  @TempDir Path dir;

  @Test
  void answersFromArt49() throws Exception {
    byte[] html = Files.readAllBytes(Path.of("src/test/resources/fixtures/cdc-art49.htm"));
    var art49 = LawParser.parse("cdc", html).stream().filter(a -> a.id().equals("cdc:art49")).findFirst().orElseThrow();

    var retriever = mock(Retriever.class);
    when(retriever.retrieve(anyString(), any(), anyInt()))
        .thenReturn(List.of(new RetrievedArticle(art49.id(), List.of(art49.text()))));
    var repo = mock(ChunkRepository.class);
    when(repo.articleTexts(any())).thenReturn(Map.of(art49.id(), art49.text()));

    var chat =
        new RecordingChat(
            ModelConfig.ollamaChat("http://localhost:11434", "qwen3.5:9b"),
            "ollama:qwen3.5:9b",
            new Recordings(dir.resolve("a.json.gz")),
            true);
    Answer r =
        new Answerer(retriever, repo, chat, "ollama")
            .answer("Qual o prazo para desistir de uma compra feita pela internet?", Mode.HYBRID);

    System.out.println(r);
    assertThat(r.failed()).isFalse();
    assertThat(r.citations()).isNotEmpty();
  }
}
