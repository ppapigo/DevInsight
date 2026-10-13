package com.portfolio.devinsight.github;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.portfolio.devinsight.common.*;
import com.portfolio.devinsight.config.AppProperties;
import java.io.*;
import java.net.http.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GitHubClientTest {
  @SuppressWarnings("unchecked")
  private HttpResponse<InputStream> response(int status, String body) {
    var r = (HttpResponse<InputStream>) mock(HttpResponse.class);
    when(r.statusCode()).thenReturn(status);
    when(r.body()).thenReturn(new ByteArrayInputStream(body.getBytes()));
    when(r.headers())
        .thenReturn(HttpHeaders.of(Map.of("retry-after", List.of("120")), (a, b) -> true));
    return r;
  }

  @Test
  void respectsRateLimitAndDoesNotRetryIt() throws Exception {
    var http = mock(HttpClient.class);
    var limited = response(429, "sensitive upstream body");
    when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(limited);
    var client = new GitHubClient(new AppProperties(), new Json(), http);
    assertThatThrownBy(() -> client.get("/users/octocat"))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e -> {
              assertThat(((ApiException) e).getRetryAfter()).isEqualTo(120);
              assertThat(e.getMessage()).doesNotContain("sensitive");
            });
    verify(http, times(1)).send(any(), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void retriesTransientFailuresWithinThreeAttempts() throws Exception {
    var http = mock(HttpClient.class);
    var unavailable = response(503, "error");
    var success = response(200, "{\"login\":\"octocat\"}");
    when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(unavailable, success);
    assertThat(
            new GitHubClient(new AppProperties(), new Json(), http)
                .get("/users/octocat")
                .path("login")
                .asText())
        .isEqualTo("octocat");
    verify(http, times(2)).send(any(), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void rejectsPathInjection() {
    assertThatThrownBy(() -> GitHubClient.validateLogin("../admin"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> GitHubClient.validateRepo("..")).isInstanceOf(ApiException.class);
  }
}
