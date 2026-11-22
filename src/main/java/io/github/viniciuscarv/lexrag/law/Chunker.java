package io.github.viniciuscarv.lexrag.law;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits an article into chunks of at most {@link #MAX} chars, breaking at inciso/paragraph lines. */
public final class Chunker {

  public static final int MAX = 1500;
  private static final Pattern BOUNDARY = Pattern.compile("^([IVXLC]+ -|§|Parágrafo único)");
  private static final Pattern LABEL = Pattern.compile("^Art\\.\\s*\\d+[º°]?(?:-[A-Za-z])?");

  private Chunker() {}

  public static List<Chunk> chunk(Article a) {
    if (a.revoked() || a.text().isBlank()) return List.of();
    List<String> lines = new ArrayList<>();
    for (String l : a.text().split("\n")) splitLong(l, lines);

    List<String> texts = new ArrayList<>();
    List<String> cur = new ArrayList<>();
    int len = 0;
    for (String line : lines) {
      while (!cur.isEmpty() && len + 1 + line.length() > MAX) {
        int cut = lastBoundary(cur);
        texts.add(String.join("\n", cur.subList(0, cut)));
        cur = new ArrayList<>(cur.subList(cut, cur.size()));
        len = cur.stream().mapToInt(s -> s.length() + 1).sum() - (cur.isEmpty() ? 0 : 1);
      }
      len += cur.isEmpty() ? line.length() : line.length() + 1;
      cur.add(line);
    }
    if (!cur.isEmpty()) texts.add(String.join("\n", cur));

    Matcher label = LABEL.matcher(a.text());
    String artLabel = label.find() ? label.group() + "\n" : "";
    List<Chunk> out = new ArrayList<>();
    for (int i = 0; i < texts.size(); i++) {
      String embed = a.path() + "\n" + (i == 0 ? "" : artLabel) + texts.get(i);
      out.add(new Chunk(a.id(), i, texts.get(i), embed));
    }
    return out;
  }

  /** Index to cut before: the last boundary line after the first, else flush everything. */
  private static int lastBoundary(List<String> lines) {
    for (int i = lines.size() - 1; i > 0; i--) {
      if (BOUNDARY.matcher(lines.get(i)).find()) return i;
    }
    return lines.size();
  }

  private static void splitLong(String line, List<String> out) {
    while (line.length() > MAX) {
      int cut = line.lastIndexOf(". ", MAX - 1) + 1; // keep the period in the head
      if (cut <= 0) cut = line.lastIndexOf(' ', MAX);
      if (cut <= 0) cut = MAX;
      out.add(line.substring(0, cut).strip());
      line = line.substring(cut).strip();
    }
    out.add(line);
  }
}
