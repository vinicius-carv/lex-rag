package io.github.viniciuscarv.lexrag.answer;

import java.util.Locale;

/** Text normalization shared by citation matching. */
public final class Text {

  private Text() {}

  public static String normalize(String s) {
    String t =
        s.toLowerCase(Locale.ROOT)
            .replaceAll("[“”„«»]", "\"")
            .replaceAll("[‘’‚]", "'")
            .replace('°', 'º')
            .replaceAll("[\\s\\u00A0]+", " ")
            .strip();
    return t.replaceAll("(?:\\s*(?:\\.\\.\\.|…))+$", "").strip();
  }
}
