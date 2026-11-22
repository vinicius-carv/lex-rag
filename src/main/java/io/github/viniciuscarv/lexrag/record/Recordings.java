package io.github.viniciuscarv.lexrag.record;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Gzipped JSON key/value store of recorded model outputs; keys are written sorted for stable diffs. */
public final class Recordings {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final Path file;
  private final Map<String, Object> data = new TreeMap<>();

  public Recordings(Path file) {
    this.file = file;
    if (Files.exists(file)) {
      try (var in = new GZIPInputStream(Files.newInputStream(file))) {
        data.putAll(MAPPER.readValue(in, new TypeReference<Map<String, Object>>() {}));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  public static String key(String model, String text) {
    try {
      byte[] h = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return model + ":" + HexFormat.of().formatHex(h);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public <T> Optional<T> get(String key, Class<T> type) {
    Object v = data.get(key);
    return v == null ? Optional.empty() : Optional.of(MAPPER.convertValue(v, type));
  }

  public void put(String key, Object value) {
    data.put(key, value);
  }

  public void save() {
    try {
      Files.createDirectories(file.toAbsolutePath().getParent());
      try (var out = new GZIPOutputStream(Files.newOutputStream(file))) {
        MAPPER.writeValue(out, data);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
