package io.github.viniciuscarv.lexrag.store;

import com.pgvector.PGvector;
import io.github.viniciuscarv.lexrag.law.Chunk;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChunkRepository {

  private final JdbcTemplate jdbc;

  public ChunkRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void reset() {
    jdbc.update("truncate chunk");
  }

  public void insert(Chunk c, float[] embedding) {
    jdbc.update(
        "insert into chunk (article_id, ordinal, text, embed_text, embedding) values (?, ?, ?, ?, ?)",
        c.articleId(), c.ordinal(), c.text(), c.embedText(), new PGvector(embedding));
  }

  /**
   * OR over the question's lexemes: layperson questions rarely contain every term, so an AND match
   * (websearch_to_tsquery) returns nothing. Ranking is length-normalised so short, focused articles
   * beat long ones. Empty when the query yields no usable tsquery terms. The ::tsquery cast (unlike to_tsquery) does not
   * re-stem lexemes that plainto_tsquery already stemmed.
   */
  public List<ScoredChunk> keyword(String query, int n) {
    return jdbc.query(
        """
        select article_id, ordinal, text, ts_rank_cd(tsv, t.q, 1) as score
        from chunk, (select replace(plainto_tsquery('portuguese', ?)::text, '&', '|')::tsquery as q) t
        where tsv @@ t.q
        order by score desc, article_id, ordinal
        limit ?""",
        (rs, i) -> new ScoredChunk(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getDouble(4)),
        query, n);
  }

  /** Exact cosine-distance scan; score is cosine similarity. */
  public List<ScoredChunk> dense(float[] query, int n) {
    PGvector v = new PGvector(query);
    return jdbc.query(
        """
        select article_id, ordinal, text, 1 - (embedding <=> ?) as score
        from chunk
        order by embedding <=> ?, article_id, ordinal
        limit ?""",
        (rs, i) -> new ScoredChunk(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getDouble(4)),
        v, v, n);
  }

  public Map<String, String> articleTexts(Collection<String> ids) {
    Map<String, String> out = new LinkedHashMap<>();
    if (ids.isEmpty()) return out;
    jdbc.query(
        "select article_id, string_agg(text, chr(10) order by ordinal) from chunk"
            + " where article_id = any (?) group by article_id order by array_position(?, article_id)",
        rs -> {
          out.put(rs.getString(1), rs.getString(2));
        },
        (Object) ids.toArray(String[]::new),
        (Object) ids.toArray(String[]::new));
    return out;
  }
}
