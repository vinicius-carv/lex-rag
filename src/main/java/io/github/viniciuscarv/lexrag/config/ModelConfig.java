package io.github.viniciuscarv.lexrag.config;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import io.github.viniciuscarv.lexrag.answer.Answerer;
import io.github.viniciuscarv.lexrag.record.RecordingChat;
import io.github.viniciuscarv.lexrag.retrieve.Retriever;
import io.github.viniciuscarv.lexrag.store.ChunkRepository;
import java.time.Duration;
import io.github.viniciuscarv.lexrag.record.RecordingEmbedder;
import io.github.viniciuscarv.lexrag.record.Recordings;
import java.nio.file.Path;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelConfig {

  @Bean
  RecordingEmbedder recordingEmbedder(
      @Value("${lexrag.embedding.model}") String model,
      @Value("${lexrag.ollama.base-url}") String ollamaUrl,
      @Value("${lexrag.recordings-dir}") String dir,
      @Value("${lexrag.record}") boolean record) {
    var live =
        record ? OllamaEmbeddingModel.builder().baseUrl(ollamaUrl).modelName(model).build() : null;
    var rec = new Recordings(Path.of(dir, "embeddings-" + model + ".json.gz"));
    return new RecordingEmbedder(live, model, rec, record);
  }

  @Bean
  Function<String, float[]> queryEmbedder(RecordingEmbedder embedder) {
    return embedder::embed;
  }

  public static ChatModel ollamaChat(String baseUrl, String model) {
    return OllamaChatModel.builder()
        .baseUrl(baseUrl)
        .modelName(model)
        .responseFormat(ResponseFormat.JSON)
        .think(false)
        .temperature(0.0)
        .timeout(Duration.ofMinutes(5))
        .build();
  }

  public static ChatModel anthropicChat(String apiKey, String model) {
    return AnthropicChatModel.builder()
        .apiKey(apiKey)
        .modelName(model)
        .temperature(0.0)
        .maxTokens(1024)
        .timeout(Duration.ofMinutes(2))
        .build();
  }

  @Bean
  Answerer answerer(
      Retriever retriever,
      ChunkRepository repo,
      @Value("${lexrag.chat.provider}") String provider,
      @Value("${lexrag.chat.ollama-model}") String ollamaModel,
      @Value("${lexrag.chat.anthropic-model}") String anthropicModel,
      @Value("${lexrag.ollama.base-url}") String ollamaUrl,
      @Value("${lexrag.recordings-dir}") String dir,
      @Value("${lexrag.record}") boolean record) {
    String model =
        switch (provider) {
          case "ollama" -> ollamaModel;
          case "anthropic" -> anthropicModel;
          default -> throw new IllegalStateException("Unknown provider '" + provider + "'; use ollama or anthropic");
        };
    ChatModel live = null;
    if (provider.equals("anthropic")) {
      String key = System.getenv("ANTHROPIC_API_KEY");
      if (key == null || key.isBlank()) {
        throw new IllegalStateException("Provider anthropic selected but ANTHROPIC_API_KEY is not set");
      }
      if (record) live = anthropicChat(key, model);
    } else if (record) {
      live = ollamaChat(ollamaUrl, model);
    }
    var rec = new Recordings(Path.of(dir, "answers-" + provider + "-" + model.replace(':', '-') + ".json.gz"));
    return new Answerer(retriever, repo, new RecordingChat(live, provider + ":" + model, rec, record), provider);
  }
}
