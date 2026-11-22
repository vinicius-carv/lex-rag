package io.github.viniciuscarv.lexrag.law;

import java.util.List;

/**
 * One article of a law. {@code text} holds the quotable text (amendment notes removed), one source
 * paragraph per line; {@code path} is the heading trail, e.g. "Título II > Capítulo I".
 */
public record Article(
    String id, String law, String path, String text, List<String> notes, boolean revoked) {}
