package io.github.viniciuscarv.lexrag.law;

/** A retrievable slice of an article; {@code embedText} adds the heading context for embedding. */
public record Chunk(String articleId, int ordinal, String text, String embedText) {}
