package com.portfolio.devinsight;

import static org.hamcrest.Matchers.*;
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
      "spring.datasource.url=jdbc:h2:mem:oauth;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.github-client-id=unit-test-client",
      "app.github-client-secret=unit-test-fixture",
      "app.admin-github-login=allowed-admin",
      "app.frontend-url=http://localhost:5173"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthConfigurationTest {
  @Autowired MockMvc mvc;

  @Test
  void configuredOAuthUsesPublicCallbackAndState() throws Exception {
    mvc.perform(get("/api/auth")).andExpect(jsonPath("$.oauthEnabled").value(true));
    mvc.perform(get("/oauth2/authorization/github"))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            header()
                .string(
                    "Location",
                    allOf(
                        startsWith("https://github.com/login/oauth/authorize"),
                        containsString("state="),
                        containsString("client_id=unit-test-client"),
                        containsString(
                            "redirect_uri=http://localhost:5173/login/oauth2/code/github"))));
  }
}
