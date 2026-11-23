package io.github.viniciuscarv.lexrag.retrieve;

public enum Mode {
  KEYWORD,
  DENSE,
  HYBRID;

  /** Best measured retrieval mode; see README results. */
  public static final Mode SYSTEM = DENSE;
}
