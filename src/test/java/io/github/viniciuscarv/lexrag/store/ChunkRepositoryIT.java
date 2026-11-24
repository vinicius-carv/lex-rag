package io.github.viniciuscarv.lexrag.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.viniciuscarv.lexrag.law.Chunk;
import io.github.viniciuscarv.lexrag.retrieve.Mode;
import io.github.viniciuscarv.lexrag.retrieve.RetrievedArticle;
import io.github.viniciuscarv.lexrag.retrieve.Retriever;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.postgresql.PostgreSQLContainer;

class ChunkRepositoryIT {

  static final PostgreSQLContainer PG = new PostgreSQLContainer("pgvector/pgvector:0.8.7-pg18");
  static final ChunkRepository repo;

  static {
    PG.start(); // one container per JVM; Ryuk reaps it
    var ds =
        DataSourceBuilder.create()
            .url(PG.getJdbcUrl())
            .username(PG.getUsername())
            .password(PG.getPassword())
            .build();
    new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
    repo = new ChunkRepository(new JdbcTemplate(ds));
  }

  static float[] unit(int axis) {
    float[] v = new float[1024];
    v[axis] = 1f;
    return v;
  }

  static Chunk chunk(String id, int ord, String text) {
    return new Chunk(id, ord, text, text);
  }

  @BeforeEach
  void seed() {
    repo.reset();
    repo.insert(chunk("cdc:art49", 0, "O consumidor pode desistir do contrato, no prazo de 7 dias, direito de arrependimento."), unit(0));
    repo.insert(chunk("cdc:art6", 0, "São direitos básicos do consumidor a proteção da vida."), unit(1));
    repo.insert(chunk("cf88:art5", 0, "Todos são iguais perante a lei."), unit(2));
    repo.insert(chunk("cf88:art5", 1, "Parágrafo segundo do artigo."), unit(3));
  }

  @Test
  void keywordRanksMatchingChunkFirst() {
    assertThat(repo.keyword("direito de arrependimento", 5).get(0).articleId()).isEqualTo("cdc:art49");
  }

  @Test
  void keywordMatchesWhenOnlySomeTermsPresent() {
    assertThat(repo.keyword("Comprei um celular pela internet e me arrependi da compra, posso devolver hoje?", 5).get(0).articleId())
        .isEqualTo("cdc:art49");
  }

  @Test
  void keywordDoesNotRestemLexemes() {
    // "preciso" stems to "precis"; re-stemming would turn that into "prec" and miss "precisa"
    repo.insert(chunk("cdc:art99", 0, "O consumidor precisa informar o fornecedor."), unit(4));
    assertThat(repo.keyword("preciso", 5)).extracting(ScoredChunk::articleId).containsExactly("cdc:art99");
  }

  @Test
  void articleTextsFollowRequestedOrder() {
    assertThat(repo.articleTexts(List.of("cf88:art5", "cdc:art6", "cdc:art49")).keySet())
        .containsExactly("cf88:art5", "cdc:art6", "cdc:art49");
  }

  @Test
  void keywordToleratesUserSyntax() {
    assertThatCode(() -> repo.keyword("O que diz o art. 5º, § 1º - \"direitos\"?", 5)).doesNotThrowAnyException();
    assertThat(repo.keyword("!!!", 5)).isEmpty();
  }

  @Test
  void denseReturnsNearestFirst() {
    var r = repo.dense(unit(2), 2);
    assertThat(r).hasSize(2);
    assertThat(r.get(0).articleId()).isEqualTo("cf88:art5");
    assertThat(r.get(0).ordinal()).isZero();
  }

  @Test
  void articleTextsJoinsPiecesInOrdinalOrder() {
    assertThat(repo.articleTexts(List.of("cf88:art5", "cdc:art6")))
        .containsEntry("cf88:art5", "Todos são iguais perante a lei.\nParágrafo segundo do artigo.")
        .hasSize(2);
  }

  @Test
  void hybridDedupesToArticleLevel() {
    // dense ranks art5#1 first then art5#0; keyword matches art5#0 only
    var r = new Retriever(repo, q -> unit(3)).retrieve("iguais perante a lei", Mode.HYBRID, 2);
    assertThat(r).extracting(RetrievedArticle::articleId).startsWith("cf88:art5").doesNotHaveDuplicates();
    assertThat(r.get(0).pieces()).hasSize(2);
  }

  @Test
  void everyModeReturnsAtMostK() {
    var rt = new Retriever(repo, q -> unit(0));
    for (Mode m : Mode.values()) {
      assertThat(rt.retrieve("consumidor direito", m, 2)).hasSizeLessThanOrEqualTo(2);
    }
  }
}
