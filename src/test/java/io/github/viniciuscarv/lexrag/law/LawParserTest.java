package io.github.viniciuscarv.lexrag.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LawParserTest {

  private static final Path MANIFEST = Path.of("corpus/laws/manifest.json");

  private static byte[] fixture(String name) throws IOException {
    try (var in = LawParserTest.class.getResourceAsStream("/fixtures/" + name)) {
      return in.readAllBytes();
    }
  }

  private static List<String> ids(List<Article> articles) {
    return articles.stream().map(Article::id).toList();
  }

  private static Article byId(List<Article> articles, String id) {
    return articles.stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
  }

  @Test
  void parsesArticleIdsAndText() throws IOException {
    List<Article> articles = LawParser.parse("cdc", fixture("cdc-art49.htm"));

    assertThat(ids(articles)).containsExactly("cdc:art48", "cdc:art49", "cdc:art50");
    Article art49 = byId(articles, "cdc:art49");
    assertThat(art49.text()).startsWith("Art. 49. O consumidor pode desistir do contrato");
    // The snapshot writes the deadline as a numeral, not "sete dias".
    assertThat(art49.text()).contains("7 dias");
    assertThat(art49.text().lines()).hasSize(2); // caput + parágrafo único
    assertThat(art49.revoked()).isFalse();
  }

  @Test
  void dropsStruckTextAndKeepsNotes() throws IOException {
    List<Article> articles = LawParser.parse("cf88", fixture("cf-art103a.htm"));

    assertThat(ids(articles)).contains("cf88:art103-a", "cf88:art103-b");
    List<String> struck =
        List.of(
            "quinze membros com mais de trinta e cinco",
            "um Ministro do Supremo Tribunal Federal, indicado pelo respectivo tribunal",
            "ação declaratória de constitucionalidade poderá ser proposta pelo Presidente");
    for (Article a : articles) {
      for (String s : struck) {
        assertThat(a.text()).as(a.id()).doesNotContain(s);
      }
    }
    Article art103a = byId(articles, "cf88:art103-a");
    assertThat(art103a.notes()).isNotEmpty();
    assertThat(art103a.text()).doesNotContain("Incluído pela").doesNotContain("Redação dada");
    assertThat(art103a.text()).doesNotContain("Vide Lei");
    assertThat(art103a.text().lines()).hasSize(4); // caput + §1..§3
  }

  @Test
  void stopsAtAdct() throws IOException {
    List<Article> articles = LawParser.parse("cf88", fixture("cf-adct.htm"));

    assertThat(ids(articles).getLast()).isEqualTo("cf88:art250");
    assertThat(ids(articles)).doesNotContain("cf88:art1");
    // The signature line after art. 250 must not leak into its text.
    assertThat(byId(articles, "cf88:art250").text()).doesNotContain("Brasília");
  }

  @Test
  void pathCapturesHeadings() throws IOException {
    List<Article> articles = LawParser.parse("cdc", fixture("cdc-art49.htm"));

    assertThat(articles).allSatisfy(a -> assertThat(a.path()).contains("Capítulo"));
    assertThat(articles.getFirst().path())
        .isEqualTo("Capítulo VI - Da Proteção Contratual > Seção I - Disposições Gerais");
  }

  @Test
  void duplicateIdFailsNamingTheId() {
    byte[] html =
        "<p>Art. 1º Um.</p><p>Art. 1º Outro.</p>".getBytes(java.nio.charset.Charset.forName("windows-1252"));

    assertThatThrownBy(() -> LawParser.parse("x", html))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("x:art1");
  }

  @Test
  void flagsRevokedArticles() {
    byte[] html =
        "<p>Art. 5º (Revogado pela Emenda nº 1)</p><p>Art. 6º Texto vigente.</p>"
            .getBytes(java.nio.charset.Charset.forName("windows-1252"));

    List<Article> articles = LawParser.parse("x", html);

    assertThat(byId(articles, "x:art5").revoked()).isTrue();
    assertThat(byId(articles, "x:art6").revoked()).isFalse();
  }

  @Test
  void realSnapshotCounts() throws Exception {
    List<Article> all = LawParser.load(MANIFEST);

    assertThat(ids(all)).doesNotHaveDuplicates();
    assertThat(ids(all))
        .containsAll(IntStream.rangeClosed(1, 119).mapToObj(n -> "cdc:art" + n).toList());
    assertThat(ids(all))
        .containsAll(IntStream.rangeClosed(1, 250).mapToObj(n -> "cf88:art" + n).toList());

    JsonNode manifest = JsonMapper.builder().build().readTree(Files.readString(MANIFEST));
    for (JsonNode e : manifest) {
      byte[] bytes = Files.readAllBytes(MANIFEST.resolveSibling(e.get("file").asString()));
      String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      assertThat(sha).as(e.get("file").asString()).isEqualTo(e.get("sha256").asString());
    }
  }
}
