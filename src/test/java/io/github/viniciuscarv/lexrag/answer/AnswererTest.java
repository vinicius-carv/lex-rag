package io.github.viniciuscarv.lexrag.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.exception.TimeoutException;
import io.github.viniciuscarv.lexrag.record.RecordingChat;
import io.github.viniciuscarv.lexrag.retrieve.Mode;
import io.github.viniciuscarv.lexrag.retrieve.RetrievedArticle;
import io.github.viniciuscarv.lexrag.retrieve.Retriever;
import io.github.viniciuscarv.lexrag.store.ChunkRepository;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnswererTest {

  private static final String GOOD =
      "{\"answer\":\"a\",\"insufficient\":false,\"citations\":["
          + "{\"articleId\":\"x:art1\",\"quote\":\"a primeira regra do artigo\"},"
          + "{\"articleId\":\"x:art2\",\"quote\":\"inventada\"}]}";

  Retriever retriever = mock(Retriever.class);
  ChunkRepository repo = mock(ChunkRepository.class);
  RecordingChat chat = mock(RecordingChat.class);
  Answerer answerer = new Answerer(retriever, repo, chat, "ollama");

  @BeforeEach
  void setUp() {
    // 'a' and 'b' runs stand in for article text so the prompt budget can be measured
    when(retriever.retrieve(anyString(), any(), anyInt()))
        .thenReturn(
            List.of(
                new RetrievedArticle("x:art1", List.of("a".repeat(4000))),
                new RetrievedArticle("x:art2", List.of("b".repeat(4000)))));
    when(repo.articleTexts(any()))
        .thenReturn(
            Map.of("x:art1", "Esta é a primeira regra do artigo.", "x:art2", "segundo texto"));
  }

  @Test
  void retriesOnceThenSucceedsWithVerifiedCitations() {
    when(chat.complete(anyString())).thenReturn("garbage", GOOD);
    Answer r = answerer.answer("q?", Mode.HYBRID);
    assertThat(r.failed()).isFalse();
    assertThat(r.citations())
        .extracting(VerifiedCitation::status)
        .containsExactly(CitationStatus.VERIFIED, CitationStatus.UNSUPPORTED);
    assertThat(r.retrievedIds()).containsExactly("x:art1", "x:art2");
    var prompts = ArgumentCaptor.forClass(String.class);
    verify(chat, times(2)).complete(prompts.capture());
    assertThat(prompts.getAllValues().get(1)).startsWith(prompts.getAllValues().get(0));
  }

  @Test
  void failsAfterTwoGarbageReplies() {
    when(chat.complete(anyString())).thenReturn("garbage");
    Answer r = answerer.answer("q?", Mode.HYBRID);
    assertThat(r.failed()).isTrue();
    assertThat(r.insufficient()).isFalse();
    assertThat(r.citations()).isEmpty();
    verify(chat, times(2)).complete(anyString());
  }

  @Test
  void promptHoldsBothIdsAndCapsArticleText() {
    when(chat.complete(anyString())).thenReturn(GOOD);
    answerer.answer("q?", Mode.HYBRID);
    var p = ArgumentCaptor.forClass(String.class);
    verify(chat).complete(p.capture());
    String prompt = p.getValue();
    assertThat(prompt).contains("[x:art1]", "[x:art2]", "q?");
    int total = 0;
    for (var m = Pattern.compile("[ab]{50,}").matcher(prompt); m.find(); ) {
      total += m.group().length();
    }
    assertThat(total).isEqualTo(6000);
  }

  @Test
  void insufficientStillVerifiesCitations() {
    when(chat.complete(anyString()))
        .thenReturn(
            "{\"answer\":\"n/a\",\"insufficient\":true,\"citations\":[{\"articleId\":\"x:art1\",\"quote\":\"q\"}]}");
    Answer r = answerer.answer("q?", Mode.KEYWORD);
    assertThat(r.insufficient()).isTrue();
    assertThat(r.citations()).hasSize(1);
    verify(retriever).retrieve(eq("q?"), eq(Mode.KEYWORD), eq(5));
  }

  @Test
  void chatExceptionCountsAsFailure() {
    when(chat.complete(anyString())).thenThrow(new TimeoutException("timeout"));
    Answer r = answerer.answer("q?", Mode.HYBRID);
    assertThat(r.failed()).isTrue();
    assertThat(r.citations()).isEmpty();
    verify(chat, times(2)).complete(anyString());

    org.mockito.Mockito.reset(chat);
    when(chat.complete(anyString())).thenThrow(new TimeoutException("timeout")).thenReturn(GOOD);
    assertThat(answerer.answer("q?", Mode.HYBRID).failed()).isFalse();
  }

  @Test
  void replayMissPropagates() {
    when(chat.complete(anyString())).thenThrow(new IllegalStateException("no recording"));
    assertThatThrownBy(() -> answerer.answer("q?", Mode.HYBRID)).isInstanceOf(IllegalStateException.class);
  }
}
