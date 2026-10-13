package com.portfolio.devinsight.github;

import com.portfolio.devinsight.common.*;
import com.portfolio.devinsight.config.AppProperties;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class GitHubClient {
  private final HttpClient http;
  private final AppProperties props;
  private final Json json;

  @org.springframework.beans.factory.annotation.Autowired
  public GitHubClient(AppProperties props, Json json) {
    this(
        props,
        json,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  GitHubClient(AppProperties props, Json json, HttpClient http) {
    this.props = props;
    this.json = json;
    this.http = http;
  }

  public static String segment(String value) {
    return URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
  }

  public static void validateLogin(String value) {
    if (value == null || !value.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?"))
      throw ApiException.badRequest("유효한 GitHub 사용자명을 입력해 주세요.");
  }

  public static void validateRepo(String value) {
    if (value == null
        || !value.matches("[A-Za-z0-9_.-]{1,100}")
        || value.equals(".")
        || value.equals("..")) throw ApiException.badRequest("유효한 저장소 이름을 입력해 주세요.");
  }

  public JsonNode get(String path) {
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        var builder =
            HttpRequest.newBuilder(URI.create("https://api.github.com" + path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "DevInsight-AI");
        if (!props.getGithubToken().isBlank())
          builder.header("Authorization", "Bearer " + props.getGithubToken());
        var response = http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        try (var body = response.body()) {
          if (status == 403 || status == 429) {
            long reset =
                response
                    .headers()
                    .firstValue("x-ratelimit-reset")
                    .map(v -> parseLong(v, Instant.now().getEpochSecond() + 60))
                    .orElse(Instant.now().getEpochSecond() + 60);
            long retry =
                response
                    .headers()
                    .firstValue("retry-after")
                    .map(v -> parseLong(v, 60))
                    .orElse(Math.max(60, reset - Instant.now().getEpochSecond()));
            throw new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "GITHUB_RATE_LIMIT",
                "GitHub API 호출 제한입니다. 잠시 후 다시 시도해 주세요.",
                Math.min(3600, retry));
          }
          if (status == 404)
            throw new ApiException(
                HttpStatus.NOT_FOUND, "GITHUB_NOT_FOUND", "공개 GitHub 계정 또는 저장소를 찾을 수 없습니다.");
          if (status == 409)
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_REPOSITORY", "커밋이 없는 저장소는 분석할 수 없습니다.");
          if (status >= 500 && attempt < 2) {
            pause(attempt);
            continue;
          }
          if (status != 200)
            throw new ApiException(
                HttpStatus.BAD_GATEWAY, "GITHUB_ERROR", "GitHub 요청을 완료하지 못했습니다.");
          byte[] data = body.readNBytes(4_000_001);
          if (data.length > 4_000_000)
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "REPOSITORY_TOO_LARGE",
                "저장소 메타데이터가 수집 한도를 초과했습니다.");
          return json.tree(new String(data, java.nio.charset.StandardCharsets.UTF_8));
        }
      } catch (IOException e) {
        if (attempt == 2)
          throw new ApiException(
              HttpStatus.BAD_GATEWAY, "GITHUB_UNAVAILABLE", "GitHub 연결에 실패했습니다. 다시 시도할 수 있습니다.");
        pause(attempt);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INTERRUPTED", "요청이 중단되었습니다.");
      }
    }
    throw new IllegalStateException("Unreachable");
  }

  private static long parseLong(String value, long fallback) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static void pause(int attempt) {
    try {
      Thread.sleep(250L * (attempt + 1));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
