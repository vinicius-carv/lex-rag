package io.github.viniciuscarv.lexrag.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.viniciuscarv.lexrag.answer.Answer;
import io.github.viniciuscarv.lexrag.answer.Answerer;
import io.github.viniciuscarv.lexrag.answer.CitationStatus;
import io.github.viniciuscarv.lexrag.answer.VerifiedCitation;
import io.github.viniciuscarv.lexrag.retrieve.Mode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AskController.class)
class AskControllerTest {

  @Autowired MockMvc mvc;
  @MockitoBean Answerer answerer;

  @Test
  void answers() throws Exception {
    var cite = new VerifiedCitation("cdc:art49", "cdc:art49", "sete dias", CitationStatus.VERIFIED);
    when(answerer.answer(eq("prazo?"), eq(Mode.SYSTEM)))
        .thenReturn(
            new Answer(
                "prazo?",
                Mode.SYSTEM,
                "ollama",
                "Sete dias.",
                false,
                false,
                List.of(cite),
                List.of("cdc:art49")));
    mvc.perform(
            post("/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"prazo?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").value("Sete dias."))
        .andExpect(jsonPath("$.citations[0].status").value("VERIFIED"))
        .andExpect(jsonPath("$.retrievedIds[0]").value("cdc:art49"));
  }

  @Test
  void blankQuestionIs400() throws Exception {
    mvc.perform(post("/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unknownModeIs400() throws Exception {
    mvc.perform(
            post("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"x\",\"mode\":\"nope\"}"))
        .andExpect(status().isBadRequest());
  }
}
