package com.portfolio.devinsight.security;

import com.portfolio.devinsight.config.AppProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain security(HttpSecurity http, AppProperties props) throws Exception {
    var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrf.setCookieCustomizer(c -> c.sameSite("Lax").path("/"));
    http.csrf(
            c ->
                c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/admin/**", "/api/github/**", "/api/analysis/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/api/auth",
                        "/api/projects",
                        "/api/projects/*",
                        "/api/portfolio/overview",
                        "/api/documents/*/content",
                        "/actuator/health")
                    .permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/interview/ask")
                    .permitAll()
                    .requestMatchers("/oauth2/**", "/login/**", "/error")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter()
                              .write(
                                  "{\"code\":\"AUTH_REQUIRED\",\"message\":\"관리자 로그인이 필요합니다.\"}");
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter()
                              .write(
                                  "{\"code\":\"ACCESS_DENIED\",\"message\":\"권한 또는 CSRF 토큰을 확인해"
                                      + " 주세요.\"}");
                        }))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
        .logout(
            l ->
                l.logoutUrl("/api/logout")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)));
    if (props.oauthEnabled()) {
      var client =
          CommonOAuth2Provider.GITHUB
              .getBuilder("github")
              .clientId(props.getGithubClientId())
              .clientSecret(props.getGithubClientSecret())
              .redirectUri(props.getFrontendUrl() + "/login/oauth2/code/{registrationId}")
              .scope("read:user")
              .build();
      var delegate = new DefaultOAuth2UserService();
      http.oauth2Login(
          o ->
              o.clientRegistrationRepository(new InMemoryClientRegistrationRepository(client))
                  .userInfoEndpoint(
                      u ->
                          u.userService(
                              request -> {
                                var user = delegate.loadUser(request);
                                String login = user.getAttribute("login");
                                if (login == null
                                    || !login.equalsIgnoreCase(props.getAdminGithubLogin())) {
                                  throw new OAuth2AuthenticationException(
                                      new OAuth2Error("access_denied"), "Account is not allowed");
                                }
                                return new DefaultOAuth2User(
                                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                                    user.getAttributes(),
                                    "login");
                              }))
                  .defaultSuccessUrl(props.getFrontendUrl() + "/admin", true)
                  .failureUrl(props.getFrontendUrl() + "/admin?auth=failed"));
    }
    return http.build();
  }
}
