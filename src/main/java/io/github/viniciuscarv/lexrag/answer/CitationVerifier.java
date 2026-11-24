package io.github.viniciuscarv.lexrag.answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Checks that each quoted passage really appears in the article it is attributed to. */
public final class CitationVerifier {

  private static final double FUZZY_THRESHOLD = 0.8;
  // Shorter quotes would VERIFY by trivial substring match ("lei", "de").
  private static final int MIN_QUOTE_TOKENS = 4;

  private CitationVerifier() {}

  public static List<VerifiedCitation> verify(
      List<Citation> citations, Map<String, String> retrievedTexts) {
    Map<String, String> norm = new LinkedHashMap<>();
    retrievedTexts.forEach((id, t) -> norm.put(id, Text.normalize(t)));

    List<VerifiedCitation> out = new ArrayList<>();
    for (Citation c : citations) {
      out.add(check(c, norm));
    }
    return out;
  }

  private static VerifiedCitation check(Citation c, Map<String, String> norm) {
    String q = Text.normalize(c.quote() == null ? "" : c.quote());
    String cited = norm.get(c.articleId());
    if (q.split(" ").length < MIN_QUOTE_TOKENS) return result(c, c.articleId(), CitationStatus.UNSUPPORTED);
    if (cited != null && cited.contains(q)) return result(c, c.articleId(), CitationStatus.VERIFIED);
    for (var e : norm.entrySet()) {
      if (!e.getKey().equals(c.articleId()) && e.getValue().contains(q)) {
        return result(c, e.getKey(), CitationStatus.WRONG_ARTICLE);
      }
    }
    if (cited != null && fuzzyMatch(q, cited)) return result(c, c.articleId(), CitationStatus.FUZZY);
    return result(c, c.articleId(), CitationStatus.UNSUPPORTED);
  }

  private static VerifiedCitation result(Citation c, String id, CitationStatus s) {
    return new VerifiedCitation(id, c.articleId(), c.quote(), s);
  }

  /** Token-multiset overlap of the quote against every same-length window of the article. */
  private static boolean fuzzyMatch(String quote, String article) {
    String[] q = quote.split(" ");
    String[] t = article.split(" ");
    Map<String, Integer> need = new HashMap<>();
    for (String w : q) need.merge(w, 1, Integer::sum);
    int n = Math.min(q.length, t.length);
    Map<String, Integer> win = new HashMap<>();
    int overlap = 0;
    for (int i = 0; i < t.length; i++) {
      overlap += add(win, need, t[i]);
      if (i >= n) overlap -= remove(win, need, t[i - n]);
      if (overlap >= FUZZY_THRESHOLD * q.length) return true;
    }
    return false;
  }

  private static int add(Map<String, Integer> win, Map<String, Integer> need, String w) {
    int c = win.merge(w, 1, Integer::sum);
    return c <= need.getOrDefault(w, 0) ? 1 : 0;
  }

  private static int remove(Map<String, Integer> win, Map<String, Integer> need, String w) {
    int c = win.merge(w, -1, Integer::sum);
    return c < need.getOrDefault(w, 0) ? 1 : 0;
  }
}
