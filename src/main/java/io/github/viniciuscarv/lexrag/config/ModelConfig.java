package io.github.viniciuscarv.lexrag.config;

import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
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
}
