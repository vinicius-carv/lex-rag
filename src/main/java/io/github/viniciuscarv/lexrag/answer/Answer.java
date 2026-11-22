package io.github.viniciuscarv.lexrag.answer;

import io.github.viniciuscarv.lexrag.retrieve.Mode;
import java.util.List;

public record Answer(
    String question,
    Mode mode,
    String provider,
    String answer,
    boolean insufficient,
    boolean failed,
    List<VerifiedCitation> citations,
    List<String> retrievedIds) {}
