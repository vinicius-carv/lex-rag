package io.github.viniciuscarv.lexrag.record;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.ArrayList;
import java.util.List;

/** Embeds via recordings; calls the live model only in record mode, otherwise a miss is an error. */
public class RecordingEmbedder {

  static final int BATCH = 32;

  private final EmbeddingModel live;
  private final String modelId;
  private final Recordings rec;
  private final boolean record;

  /** {@code live} may be null when {@code record} is false. */
  public RecordingEmbedder(EmbeddingModel live, String modelId, Recordings rec, boolean record) {
    this.live = live;
    this.modelId = modelId;
    this.rec = rec;
    this.record = record;
  }

  public float[] embed(String text) {
    return embedAll(List.of(text)).get(0);
  }

  public List<float[]> embedAll(List<String> texts) {
    float[][] out = new float[texts.size()][];
    List<Integer> misses = new ArrayList<>();
    for (int i = 0; i < texts.size(); i++) {
      out[i] = rec.get(Recordings.key(modelId, texts.get(i)), float[].class).orElse(null);
      if (out[i] == null) misses.add(i);
    }
    if (!misses.isEmpty() && !record) {
      String t = texts.get(misses.get(0));
      throw new IllegalStateException(
          "No recorded embedding for key "
              + Recordings.key(modelId, t)
              + " (text: "
              + t.substring(0, Math.min(40, t.length()))
              + "); re-run with --record");
    }
    for (int s = 0; s < misses.size(); s += BATCH) {
      List<Integer> batch = misses.subList(s, Math.min(s + BATCH, misses.size()));
      List<Embedding> got =
          live.embedAll(batch.stream().map(i -> TextSegment.from(texts.get(i))).toList()).content();
      for (int j = 0; j < batch.size(); j++) {
        int i = batch.get(j);
        out[i] = got.get(j).vector();
        rec.put(Recordings.key(modelId, texts.get(i)), out[i]);
      }
    }
    return List.of(out);
  }

  /** Persists new recordings; a no-op outside record mode. */
  public void save() {
    if (record) rec.save();
  }
}
