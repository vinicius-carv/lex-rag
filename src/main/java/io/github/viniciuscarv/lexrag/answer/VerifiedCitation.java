package io.github.viniciuscarv.lexrag.answer;

/** {@code articleId} is the corrected id; {@code citedArticleId} is what the model wrote. */
public record VerifiedCitation(
    String articleId, String citedArticleId, String quote, CitationStatus status) {}
