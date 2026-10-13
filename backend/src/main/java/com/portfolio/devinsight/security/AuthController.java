package com.portfolio.devinsight.security;

import com.portfolio.devinsight.config.AppProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
  private final AppProperties props;

  public AuthController(AppProperties props) {
    this.props = props;
  }

  public record Session(
      boolean admin, boolean oauthEnabled, boolean aiConfigured, String login, String csrfToken) {}

  @GetMapping("/api/auth")
  Session session(Authentication auth, CsrfToken csrf) {
    boolean admin = Access.isAdmin(auth);
    return new Session(
        admin,
        props.oauthEnabled(),
        admin && !props.getOpenaiApiKey().isBlank(),
        admin ? auth.getName() : null,
        csrf.getToken());
  }
}
