package io.github.viniciuscarv.lexrag.store;

public record ScoredChunk(String articleId, int ordinal, String text, double score) {}
