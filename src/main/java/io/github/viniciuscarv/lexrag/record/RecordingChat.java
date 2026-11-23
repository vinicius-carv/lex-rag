package io.github.viniciuscarv.lexrag.record;

import dev.langchain4j.model.chat.ChatModel;

/** Chat completions via recordings; calls the live model in record or live mode (only record persists). */
public class RecordingChat {

  private final ChatModel live;
  private final String providerModel;
  private final Recordings rec;
  private final boolean record;
  private final boolean allowLive;

  public RecordingChat(ChatModel live, String providerModel, Recordings rec, boolean record) {
    this(live, providerModel, rec, record, false);
  }

  /** {@code live} may be null when neither {@code record} nor {@code allowLive}. */
  public RecordingChat(ChatModel live, String providerModel, Recordings rec, boolean record, boolean allowLive) {
    this.live = live;
    this.providerModel = providerModel;
    this.rec = rec;
    this.record = record;
    this.allowLive = allowLive;
  }

  public String complete(String prompt) {
    String key = Recordings.key(providerModel, prompt);
    var hit = rec.get(key, String.class);
    if (hit.isPresent()) return hit.get();
    if (!record && !allowLive) {
      throw new IllegalStateException("No recorded answer for key " + key + "; re-run with --record");
    }
    String out = live.chat(prompt);
    if (record) {
      rec.put(key, out);
      rec.save();
    }
    return out;
  }
}
