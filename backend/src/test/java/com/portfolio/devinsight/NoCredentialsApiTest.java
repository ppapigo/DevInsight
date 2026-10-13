package com.portfolio.devinsight;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.url=jdbc:h2:mem:nokey;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoCredentialsApiTest {
  @Autowired MockMvc mvc;

  @Test
  void oversizedJsonIsRejectedBeforeParsing() throws Exception {
    mvc.perform(
            post("/api/interview/ask")
                .servletPath("/api/interview/ask")
                .with(csrf())
                .contentType("application/json")
                .content("{\"question\":\"" + "x".repeat(9000) + "\"}"))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
  }

  @Test
  void applicationStartsWithoutExternalCredentials() throws Exception {
    mvc.perform(get("/api/portfolio/overview")).andExpect(status().isOk());
    mvc.perform(
            post("/api/interview/ask")
                .with(csrf())
                .contentType("application/json")
                .content("{\"question\":\"Spring Security 구현이 있나요?\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
  }
}
