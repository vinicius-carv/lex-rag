package io.github.viniciuscarv.lexrag.answer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CitationVerifierTest {

  private static final Map<String, String> TEXTS = new LinkedHashMap<>();

  static {
    TEXTS.put(
        "cdc:art49",
        "Art. 49. O consumidor pode desistir do contrato, no prazo de 7 dias a contar de sua"
            + " assinatura ou do ato de recebimento do produto ou serviço.");
    TEXTS.put("cf88:art5", "Art. 5º Todos são iguais perante a lei, sem distinção de qualquer natureza.");
  }

  private static VerifiedCitation verify(String id, String quote) {
    return CitationVerifier.verify(List.of(new Citation(id, quote)), TEXTS).getFirst();
  }

  @Test
  void exactSubstringIsVerified() {
    VerifiedCitation v = verify("cdc:art49", "O consumidor pode desistir do contrato");
    assertThat(v.status()).isEqualTo(CitationStatus.VERIFIED);
    assertThat(v.articleId()).isEqualTo("cdc:art49");
  }

  @Test
  void whitespaceAndCaseDifferencesAreVerified() {
    assertThat(verify("cdc:art49", "O  CONSUMIDOR pode\n desistir do contrato").status())
        .isEqualTo(CitationStatus.VERIFIED);
  }

  @Test
  void oneWordChangedOutOfTwelveIsFuzzy() {
    // 12 words, "desistir" replaced by "rescindir"
    String q = "O consumidor pode rescindir do contrato, no prazo de 7 dias a contar";
    assertThat(verify("cdc:art49", q).status()).isEqualTo(CitationStatus.FUZZY);
  }

  @Test
  void quoteFromAnotherRetrievedArticleIsWrongArticle() {
    VerifiedCitation v = verify("cdc:art49", "Todos são iguais perante a lei");
    assertThat(v.status()).isEqualTo(CitationStatus.WRONG_ARTICLE);
    assertThat(v.articleId()).isEqualTo("cf88:art5");
    assertThat(v.citedArticleId()).isEqualTo("cdc:art49");
  }

  @Test
  void unsupportedCases() {
    assertThat(verify("cdc:art49", "frase que não existe em lugar nenhum do corpus").status())
        .isEqualTo(CitationStatus.UNSUPPORTED);
    assertThat(verify("cdc:art999", "frase que não existe em lugar nenhum").status())
        .isEqualTo(CitationStatus.UNSUPPORTED);
    assertThat(verify("cdc:art49", "   ").status()).isEqualTo(CitationStatus.UNSUPPORTED);
    // fewer than 4 tokens: a substring of the article, but too short to count as a quote
    assertThat(verify("cdc:art49", "o consumidor pode").status()).isEqualTo(CitationStatus.UNSUPPORTED);
  }
}
