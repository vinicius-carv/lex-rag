package io.github.viniciuscarv.lexrag.record;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.ChatModel;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordingChatTest {

  @TempDir Path dir;

  @Test
  void liveModeCallsModelOnMissWithoutPersisting() {
    Path f = dir.resolve("a.json.gz");
    ChatModel fake = new ChatModel() {
      @Override
      public String chat(String prompt) {
        return "live answer";
      }
    };
    var chat = new RecordingChat(fake, "p:m", new Recordings(f), false, true);
    assertThat(chat.complete("q")).isEqualTo("live answer");
    assertThat(new Recordings(f).get(Recordings.key("p:m", "q"), String.class)).isEmpty();
  }
}
