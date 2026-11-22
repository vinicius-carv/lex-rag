package io.github.viniciuscarv.lexrag.law;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Turns a Planalto consolidated-law snapshot (windows-1252 HTML) into one {@link Article} each. */
public final class LawParser {

  private static final Charset CP1252 = Charset.forName("windows-1252");
  private static final String ADCT = "ATO DAS DISPOSIÇÕES CONSTITUCIONAIS TRANSITÓRIAS";
  private static final Pattern ARTICLE =
      Pattern.compile("^Art\\.\\s*(\\d+)(?:[º°o])?(?:\\s*-\\s*([A-Z]))?\\.?(?=\\s|$)\\s*");
  private static final Pattern HEADING =
      Pattern.compile(
          "^(TÍTULO|CAPÍTULO|SEÇÃO|SUBSEÇÃO)\\s+([IVXLCDM]+(?:-[A-Z])?|ÚNICO|\\d+)\\b\\s*(.{0,150})$",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final List<String> LEVELS = List.of("TÍTULO", "CAPÍTULO", "SEÇÃO", "SUBSEÇÃO");
  // One level of nested parentheses is enough for "(Redação dada ... (Vide ...))".
  private static final Pattern NOTE =
      Pattern.compile(
          "\\((?:Redação dada|Incluído|Incluída|Revogado|Revogada|Vide|Regulamento)"
              + "(?:[^()]|\\([^()]*\\))*\\)\\.?");
  private static final Pattern EMPTY_BODY =
      Pattern.compile("^(\\(?(?:Revogad[oa]|Vetado)\\)?)?$", Pattern.CASE_INSENSITIVE);

  private LawParser() {}

  /** Parses every law in the manifest, reading the snapshot files next to it. */
  public static List<Article> load(Path manifest) throws IOException {
    JsonNode entries = JsonMapper.builder().build().readTree(Files.readString(manifest));
    List<Article> all = new ArrayList<>();
    for (JsonNode e : entries) {
      byte[] html = Files.readAllBytes(manifest.resolveSibling(e.get("file").asString()));
      all.addAll(parse(e.get("law").asString(), html));
    }
    return all;
  }

  public static List<Article> parse(String lawId, byte[] html) {
    Document doc = Jsoup.parse(new String(html, CP1252));

    List<Article> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    String[] levels = new String[LEVELS.size()];
    int pendingTitle = -1; // heading level whose title is expected on the next non-article block
    Draft cur = null;

    for (Element p : doc.select("p")) {
      String full = clean(p.text());
      p.select("strike, s, del").remove();
      String raw = clean(p.text());
      if (raw.contains(ADCT)) {
        break;
      }
      List<String> blockNotes = new ArrayList<>();
      String text = clean(extractNotes(raw, blockNotes));
      // A fully revoked article has its "Art. N" struck and only the "(Revogado ...)" note left:
      // keep the article start so it is still ingested, flagged revoked.
      Matcher struck = ARTICLE.matcher(full);
      if (!ARTICLE.matcher(text).find()
          && struck.find()
          && blockNotes.stream().anyMatch(n -> n.startsWith("(Revogad"))) {
        text = clean(struck.group() + " " + text);
      }
      if (text.isEmpty()) {
        if (cur != null) cur.notes.addAll(blockNotes);
        continue;
      }
      if (text.startsWith("Brasília,")) { // signature block after the last article
        finish(cur, lawId, out, seen);
        cur = null;
        pendingTitle = -1;
        continue;
      }
      Matcher h = HEADING.matcher(text);
      if (h.matches()) {
        finish(cur, lawId, out, seen);
        cur = null;
        int lv = LEVELS.indexOf(h.group(1).toUpperCase(Locale.ROOT));
        for (int i = lv; i < levels.length; i++) levels[i] = null;
        String label = h.group(1).charAt(0) + h.group(1).substring(1).toLowerCase(Locale.ROOT);
        levels[lv] = label + " " + h.group(2).toUpperCase(Locale.ROOT);
        String title = h.group(3).strip();
        if (title.isEmpty()) pendingTitle = lv;
        else levels[lv] += " - " + title;
        continue;
      }
      Matcher a = ARTICLE.matcher(text);
      if (a.find()) {
        finish(cur, lawId, out, seen);
        pendingTitle = -1;
        String suffix = a.group(2) == null ? "" : "-" + a.group(2).toLowerCase(Locale.ROOT);
        cur = new Draft(lawId + ":art" + a.group(1) + suffix, path(levels), a.end());
        cur.lines.add(text);
        cur.notes.addAll(blockNotes);
      } else if (pendingTitle >= 0) {
        levels[pendingTitle] += " - " + text;
        pendingTitle = -1;
      } else if (cur != null) {
        cur.lines.add(text);
        cur.notes.addAll(blockNotes);
      }
    }
    finish(cur, lawId, out, seen);
    return out;
  }

  private static String extractNotes(String text, List<String> notes) {
    Matcher m = NOTE.matcher(text);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      notes.add(m.group().strip());
      m.appendReplacement(sb, " ");
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private static String clean(String s) {
    return s.replaceAll("[\\s\\u00A0]+", " ").strip();
  }

  private static String path(String[] levels) {
    List<String> parts = new ArrayList<>();
    for (String l : levels) if (l != null) parts.add(l);
    return String.join(" > ", parts);
  }

  private static void finish(Draft d, String law, List<Article> out, Set<String> seen) {
    if (d == null) return;
    if (!seen.add(d.id)) {
      throw new IllegalStateException("Duplicate article id " + d.id);
    }
    String text = String.join("\n", d.lines);
    String body =
        text.substring(Math.min(d.prefixEnd, text.length())).replaceAll("^[.\\s]+|[.\\s]+$", "");
    boolean revoked = EMPTY_BODY.matcher(body).matches();
    if (body.isEmpty()) { // drop the stray "." left where the struck text was
      text = text.substring(0, Math.min(d.prefixEnd, text.length())).strip();
    }
    out.add(new Article(d.id, law, d.path, text, List.copyOf(d.notes), revoked));
  }

  private static final class Draft {
    final String id;
    final String path;
    final int prefixEnd; // end of the "Art. N." prefix in the first line
    final List<String> lines = new ArrayList<>();
    final List<String> notes = new ArrayList<>();

    Draft(String id, String path, int prefixEnd) {
      this.id = id;
      this.path = path;
      this.prefixEnd = prefixEnd;
    }
  }
}
