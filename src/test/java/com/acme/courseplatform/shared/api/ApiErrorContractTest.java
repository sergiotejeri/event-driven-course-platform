package com.acme.courseplatform.shared.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiErrorContractTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();
  }

  @Test
  void returnsProblemDetailWithCorrelationForMissingResource() throws Exception {
    mvc.perform(get("/probe/missing").header("X-Correlation-Id", "client-correlation-1"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("X-Correlation-Id", "client-correlation-1"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").value("client-correlation-1"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  void returnsFieldErrorsForInvalidBody() throws Exception {
    mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(header().exists("X-Correlation-Id"))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.name").value("must not be blank"));
  }

  @Test
  void hidesDatabaseDetailsForUnknownIntegrityConflict() throws Exception {
    mvc.perform(get("/probe/integrity"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("DATA_INTEGRITY_CONFLICT"))
        .andExpect(jsonPath("$.detail").value("The operation conflicts with existing data"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("sensitive SQL"))));
  }

  @RestController
  @RequestMapping("/probe")
  static class ProbeController {

    @GetMapping("/missing")
    void missing() {
      throw new ResourceNotFoundException("Course", "missing");
    }

    @GetMapping("/integrity")
    void integrity() {
      throw new DataIntegrityViolationException("sensitive SQL");
    }

    @PostMapping
    void validate(@Valid @RequestBody ProbeRequest request) {}
  }

  record ProbeRequest(@NotBlank String name) {}
}
