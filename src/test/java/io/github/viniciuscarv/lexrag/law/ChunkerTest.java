package io.github.viniciuscarv.lexrag.law;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.viniciuscarv.lexrag.answer.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ChunkerTest {

  private static final String PATH = "Título II > Capítulo I";

  private static Article article(String id, String text, boolean revoked) {
    return new Article(id, "x", PATH, text, List.of(), revoked);
  }

  @Test
  void shortArticleIsOneChunk() {
    String text = "Art. 1º " + "a".repeat(292);
    List<Chunk> chunks = Chunker.chunk(article("x:art1", text, false));

    assertThat(chunks).hasSize(1);
    assertThat(chunks.getFirst().ordinal()).isZero();
    assertThat(chunks.getFirst().text()).isEqualTo(text);
    assertThat(chunks.getFirst().embedText()).startsWith(PATH + "\n");
  }

  @Test
  void longArticleSplitsAtIncisoOrParagraph() {
    List<String> lines = new ArrayList<>();
    lines.add("Art. 5º Todos são iguais perante a lei, sem distinção de qualquer natureza:");
    String[] romans = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    for (int i = 0; i < 60; i++) {
      lines.add(romans[i % 10] + " - " + "texto do inciso numero " + i + " " + "x".repeat(20) + ";");
    }
    lines.add("§ 1º As normas definidoras dos direitos têm aplicação imediata.");
    String text = String.join("\n", lines);
    assertThat(text.length()).isGreaterThan(1500);

    List<Chunk> chunks = Chunker.chunk(article("x:art5", text, false));

    assertThat(chunks.size()).isGreaterThan(1);
    Pattern boundary = Pattern.compile("^([IVXLC]+ - |§|Parágrafo único)");
    for (int i = 0; i < chunks.size(); i++) {
      Chunk c = chunks.get(i);
      assertThat(c.text().length()).isLessThanOrEqualTo(1500);
      assertThat(c.ordinal()).isEqualTo(i);
      if (i > 0) {
        assertThat(boundary.matcher(c.text()).find()).as("chunk %d start", i).isTrue();
        assertThat(c.embedText()).contains("Art. 5º");
      }
    }
    String joined = String.join(" ", chunks.stream().map(Chunk::text).toList());
    assertThat(joined.replaceAll("\\s+", " ")).isEqualTo(text.replaceAll("\\s+", " "));
  }

  @Test
  void singleOverlongLineSplitsAtSentenceEnd() {
    String text = ("Frase de teste com palavras. ").repeat(100).strip();
    List<Chunk> chunks = Chunker.chunk(article("x:art2", text, false));

    assertThat(chunks.size()).isGreaterThan(1);
    assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(1500));
    assertThat(chunks.getFirst().text()).endsWith(".");
  }

  @Test
  void longIncisosNeverExceedMax() {
    String text =
        String.join(
            "\n",
            "Art. 9º Caput:",
            "I - " + "a".repeat(500),
            "II - " + "b".repeat(900),
            "III - " + "c".repeat(900));

    List<Chunk> chunks = Chunker.chunk(article("x:art9", text, false));

    assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(1500));
    String joined = String.join(" ", chunks.stream().map(Chunk::text).toList());
    assertThat(joined.replaceAll("\\s+", " ")).isEqualTo(text.replaceAll("\\s+", " "));
  }

  @Test
  void realCorpusChunksFitMax() throws Exception {
    for (Article a : LawParser.load(java.nio.file.Path.of("corpus/laws/manifest.json"))) {
      assertThat(Chunker.chunk(a))
          .as(a.id())
          .allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(1500));
    }
  }

  @Test
  void revokedArticleHasNoChunks() {
    assertThat(Chunker.chunk(article("x:art3", "Art. 3º (Revogado)", true))).isEmpty();
  }

  @Test
  void normalize() {
    assertThat(Text.normalize("  “Nenhum”\u00A0direito  ...")).isEqualTo("\"nenhum\" direito");
    assertThat(Text.normalize("Art. 5° e 6º…")).isEqualTo("art. 5º e 6º");
  }
}
