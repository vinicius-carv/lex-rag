package io.github.viniciuscarv.lexrag.retrieve;

import io.github.viniciuscarv.lexrag.store.ChunkRepository;
import io.github.viniciuscarv.lexrag.store.ScoredChunk;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class Retriever {

  static final int CANDIDATES = 50;
  static final int RRF_K = 60;

  private final ChunkRepository repo;
  private final Function<String, float[]> queryEmbedder;

  public Retriever(ChunkRepository repo, Function<String, float[]> queryEmbedder) {
    this.repo = repo;
    this.queryEmbedder = queryEmbedder;
  }

  public List<RetrievedArticle> retrieve(String question, Mode mode, int k) {
    List<ScoredChunk> ranked =
        switch (mode) {
          case KEYWORD -> repo.keyword(question, CANDIDATES);
          case DENSE -> repo.dense(queryEmbedder.apply(question), CANDIDATES);
          case HYBRID -> hybrid(question);
        };
    Map<String, List<String>> byArticle = new LinkedHashMap<>();
    for (ScoredChunk c : ranked) {
      byArticle.computeIfAbsent(c.articleId(), x -> new ArrayList<>()).add(c.text());
    }
    return byArticle.entrySet().stream()
        .limit(k)
        .map(e -> new RetrievedArticle(e.getKey(), e.getValue()))
        .toList();
  }

  private List<ScoredChunk> hybrid(String question) {
    List<ScoredChunk> kw = repo.keyword(question, CANDIDATES);
    List<ScoredChunk> dn = repo.dense(queryEmbedder.apply(question), CANDIDATES);
    Map<String, ScoredChunk> byKey = new LinkedHashMap<>();
    for (ScoredChunk c : kw) byKey.putIfAbsent(key(c), c);
    for (ScoredChunk c : dn) byKey.putIfAbsent(key(c), c);
    List<List<String>> rankings =
        List.of(kw.stream().map(Retriever::key).toList(), dn.stream().map(Retriever::key).toList());
    return Rrf.fuse(rankings, RRF_K).stream().map(byKey::get).toList();
  }

  private static String key(ScoredChunk c) {
    return c.articleId() + "#" + c.ordinal();
  }
}
