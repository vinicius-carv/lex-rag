package io.github.viniciuscarv.lexrag.record;

import dev.langchain4j.model.chat.ChatModel;

/** Chat completions via recordings; calls the live model only in record mode. */
public class RecordingChat {

  private final ChatModel live;
  private final String providerModel;
  private final Recordings rec;
  private final boolean record;

  /** {@code live} may be null when {@code record} is false. */
  public RecordingChat(ChatModel live, String providerModel, Recordings rec, boolean record) {
    this.live = live;
    this.providerModel = providerModel;
    this.rec = rec;
    this.record = record;
  }

  public String complete(String prompt) {
    String key = Recordings.key(providerModel, prompt);
    var hit = rec.get(key, String.class);
    if (hit.isPresent()) return hit.get();
    if (!record) {
      throw new IllegalStateException("No recorded answer for key " + key + "; re-run with --record");
    }
    String out = live.chat(prompt);
    rec.put(key, out);
    rec.save();
    return out;
  }
}
