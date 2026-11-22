package io.github.viniciuscarv.lexrag.eval;

import io.github.viniciuscarv.lexrag.answer.Answerer;
import io.github.viniciuscarv.lexrag.config.ModelConfig;
import io.github.viniciuscarv.lexrag.record.RecordingChat;
import io.github.viniciuscarv.lexrag.record.Recordings;
import io.github.viniciuscarv.lexrag.retrieve.Mode;
import io.github.viniciuscarv.lexrag.retrieve.Retriever;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** {@code --eval}: exit 0 on pass, 1 on gate failure or missing recording, 2 on {@code --judge} without a key. */
@Component
@ConditionalOnProperty("lexrag.eval")
public class EvalCommand implements ApplicationRunner, ExitCodeGenerator {

  private final Retriever retriever;
  private final Answerer answerer;
  private final String mode;
  private final boolean retrievalOnly;
  private final boolean judge;
  private final boolean record;
  private final String recordingsDir;
  private final String anthropicModel;
  private int exitCode;

  public EvalCommand(
      Retriever retriever,
      Answerer answerer,
      @Value("${lexrag.eval-mode}") String mode,
      @Value("${lexrag.eval-retrieval-only:false}") boolean retrievalOnly,
      @Value("${lexrag.judge:false}") boolean judge,
      @Value("${lexrag.record}") boolean record,
      @Value("${lexrag.recordings-dir}") String recordingsDir,
      @Value("${lexrag.chat.anthropic-model}") String anthropicModel) {
    this.retriever = retriever;
    this.answerer = answerer;
    this.mode = mode;
    this.retrievalOnly = retrievalOnly;
    this.judge = judge;
    this.record = record;
    this.recordingsDir = recordingsDir;
    this.anthropicModel = anthropicModel;
  }

  @Override
  public void run(ApplicationArguments args) {
    String key = System.getenv("ANTHROPIC_API_KEY");
    if (judge && (key == null || key.isBlank())) {
      System.err.println("--judge requires ANTHROPIC_API_KEY in the environment");
      exitCode = 2;
      return;
    }
    try {
      RecordingChat judgeChat = null;
      if (judge) {
        var rec = new Recordings(Path.of(recordingsDir, "judge-anthropic-" + anthropicModel + ".json.gz"));
        var live = record ? ModelConfig.anthropicChat(key, anthropicModel) : null;
        judgeChat = new RecordingChat(live, "anthropic:" + anthropicModel, rec, record);
      }
      Set<Mode> modes =
          mode.equalsIgnoreCase("all")
              ? EnumSet.allOf(Mode.class)
              : EnumSet.of(Mode.valueOf(mode.toUpperCase(Locale.ROOT)));
      var questions = Question.load(Path.of("corpus/eval/questions.jsonl"));
      EvalReport report = new EvalRunner(retriever, answerer, judgeChat, questions).run(modes, !retrievalOnly);

      Path out = Path.of("results", "eval-" + LocalDate.now() + ".json");
      Files.createDirectories(out.getParent());
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build().writeValue(out, report);
      System.out.println(report.toMarkdown());
      if (!EvalRunner.gate(report, Path.of("corpus/baseline.json"), !retrievalOnly)) exitCode = 1;
    } catch (Exception e) {
      System.err.println("Eval failed: " + e.getMessage());
      exitCode = 1;
    }
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
