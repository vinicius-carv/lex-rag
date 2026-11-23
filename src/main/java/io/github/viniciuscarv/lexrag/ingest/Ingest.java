package io.github.viniciuscarv.lexrag.ingest;

import io.github.viniciuscarv.lexrag.law.Article;
import io.github.viniciuscarv.lexrag.law.Chunk;
import io.github.viniciuscarv.lexrag.law.Chunker;
import io.github.viniciuscarv.lexrag.law.LawParser;
import io.github.viniciuscarv.lexrag.record.RecordingEmbedder;
import io.github.viniciuscarv.lexrag.store.ChunkRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Ingest {

  private static final Logger log = LoggerFactory.getLogger(Ingest.class);
  private static final Path MANIFEST = Path.of("corpus/laws/manifest.json");

  private final ChunkRepository repo;
  private final RecordingEmbedder embedder;

  public Ingest(ChunkRepository repo, RecordingEmbedder embedder) {
    this.repo = repo;
    this.embedder = embedder;
  }

  public void run() {
    List<Article> articles;
    try {
      articles = LawParser.load(MANIFEST);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    List<Chunk> chunks = articles.stream().flatMap(a -> Chunker.chunk(a).stream()).toList();
    List<float[]> vectors = embedder.embedAll(chunks.stream().map(Chunk::embedText).toList());
    repo.reset();
    for (int i = 0; i < chunks.size(); i++) repo.insert(chunks.get(i), vectors.get(i));
    log.info("ingested {} chunks from {} articles", chunks.size(), articles.size());
  }
}
