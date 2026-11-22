package io.github.viniciuscarv.lexrag.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordingsTest {

  @TempDir Path dir;

  @Test
  void roundTripsAndSortsKeys() throws IOException {
    Path f = dir.resolve("r.json.gz");
    var r = new Recordings(f);
    float[] v = {0.1f, -2.5e-7f, 1f / 3};
    r.put("b", v);
    r.put("a", new float[] {1f});
    r.save();

    byte[] raw = Files.readAllBytes(f);
    assertThat(raw[0] & 0xff).isEqualTo(0x1f);
    assertThat(raw[1] & 0xff).isEqualTo(0x8b);

    var back = new Recordings(f);
    assertThat(back.get("b", float[].class)).hasValueSatisfying(x -> assertThat(x).isEqualTo(v));
    assertThat(back.get("missing", float[].class)).isEmpty();

    String json;
    try (var in = new GZIPInputStream(Files.newInputStream(f))) {
      json = new String(in.readAllBytes());
    }
    assertThat(json.indexOf("\"a\"")).isLessThan(json.indexOf("\"b\""));
  }
}
