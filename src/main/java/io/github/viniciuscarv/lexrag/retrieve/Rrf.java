package io.github.viniciuscarv.lexrag.retrieve;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reciprocal rank fusion. */
public final class Rrf {

  private Rrf() {}

  /** Ids by descending fused score; ties keep first-appearance order (stable sort). */
  public static List<String> fuse(List<List<String>> rankings, int k) {
    Map<String, Double> scores = new LinkedHashMap<>();
    for (List<String> ranking : rankings) {
      for (int i = 0; i < ranking.size(); i++) {
        scores.merge(ranking.get(i), 1.0 / (k + i + 1), Double::sum);
      }
    }
    List<String> ids = new ArrayList<>(scores.keySet());
    ids.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
    return ids;
  }
}
