package io.github.viniciuscarv.lexrag.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordingEmbedderTest {

  @TempDir Path dir;

  @Test
  void replayHit() {
    var rec = new Recordings(dir.resolve("r.json.gz"));
    rec.put(Recordings.key("m", "hello"), new float[] {1f, 2f});
    var e = new RecordingEmbedder(null, "m", rec, false);
    assertThat(e.embed("hello")).containsExactly(1f, 2f);
  }

  @Test
  void replayMissFailsLoudly() {
    var e = new RecordingEmbedder(null, "m", new Recordings(dir.resolve("r.json.gz")), false);
    String text = "x".repeat(100);
    assertThatThrownBy(() -> e.embed(text))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(Recordings.key("m", text))
        .hasMessageContaining("x".repeat(40));
  }

  @Test
  void recordModeCallsLiveAndStores() {
    Path f = dir.resolve("r.json.gz");
    float[] vec = new float[1024];
    Arrays.fill(vec, 0.5f);
    EmbeddingModel fake =
        new EmbeddingModel() {
          @Override
          public Response<List<Embedding>> embedAll(List<TextSegment> segs) {
            return Response.from(segs.stream().map(s -> Embedding.from(vec)).toList());
          }
        };
    var e = new RecordingEmbedder(fake, "m", new Recordings(f), true);
    assertThat(e.embedAll(List.of("a", "b"))).hasSize(2);
    e.save();
    assertThat(new Recordings(f).get(Recordings.key("m", "b"), float[].class))
        .hasValueSatisfying(x -> assertThat(x).hasSize(1024).isEqualTo(vec));
  }
}
