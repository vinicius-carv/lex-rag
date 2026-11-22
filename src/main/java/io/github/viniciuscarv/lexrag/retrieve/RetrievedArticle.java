package io.github.viniciuscarv.lexrag.retrieve;

import java.util.List;

/** An article with its retrieved pieces, best first. */
public record RetrievedArticle(String articleId, List<String> pieces) {}
