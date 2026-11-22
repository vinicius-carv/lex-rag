package io.github.viniciuscarv.lexrag.retrieve;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RrfTest {

  @Test
  void fusesByReciprocalRank() {
    assertThat(Rrf.fuse(List.of(List.of("a", "b", "c"), List.of("c", "a", "d")), 60))
        .containsExactly("a", "c", "b", "d");
  }
}
