package com.portfolio.devinsight.github;

import com.portfolio.devinsight.analysis.*;
import com.portfolio.devinsight.common.*;
import com.portfolio.devinsight.config.AppProperties;
import com.portfolio.devinsight.document.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class SourceCollector {
  public record Commit(String sha, String message, String date, String author, String url) {}

  public record Activity(
      List<Commit> commits,
      int sampledCommits,
      int sampledPullRequests,
      boolean treeTruncated,
      List<String> warnings) {}

  public record Snapshot(String sha, List<Source> sources, Activity activity) {}

  private final GitHubClient client;
  private final AppProperties props;

  public SourceCollector(GitHubClient client, AppProperties props) {
    this.client = client;
    this.props = props;
  }

  public String head(GitHubRepository repo) {
    return client
        .get(
            "/repos/"
                + repo.getFullName()
                + "/commits/"
                + GitHubClient.segment(repo.getDefaultBranch()))
        .path("sha")
        .asText();
  }

  public Snapshot collect(GitHubRepository repo, String sha, List<ProjectDocument> documents) {
    String base = "/repos/" + repo.getFullName();
    var tree = client.get(base + "/git/trees/" + sha + "?recursive=1");
    List<JsonNode> files = new ArrayList<>();
    tree.path("tree")
        .forEach(
            n -> {
              if ("blob".equals(n.path("type").asText())
                  && eligible(n.path("path").asText(), n.path("size").asLong())) files.add(n);
            });
    files.sort(
        Comparator.comparingInt((JsonNode n) -> priority(n.path("path").asText()))
            .thenComparing(n -> n.path("path").asText()));
    var sources = new ArrayList<Source>();
    var warnings = new ArrayList<String>();
    int remaining = Math.max(1500, props.getMaxInputTokens() / 2);
    String listing =
        TextBudget.clip(
            files.stream()
                .map(n -> n.path("path").asText())
                .limit(100)
                .reduce("", (a, b) -> a + b + "\n"),
            1000);
    sources.add(
        new Source(
            "tree", "TREE", "Directory tree", repo.getHtmlUrl() + "/tree/" + sha, listing, null));
    remaining -= TextBudget.bytes(listing);
    // Documents are included only after explicit public approval. Private text never enters public
    // analyses.
    int documentBudget = remaining / 3;
    for (var d : documents) {
      if (!d.isPublicApproved()
          || d.getExtractedText() == null
          || d.getExtractedText().isBlank()
          || remaining < 300) continue;
      if (documentBudget < 100) break;
      String text =
          TextBudget.clip(TextBudget.redact(d.getExtractedText()), Math.min(1500, documentBudget));
      sources.add(
          new Source(
              "doc-" + d.getId(),
              "DOCUMENT",
              d.getOriginalName(),
              "/api/documents/" + d.getId() + "/content",
              text,
              d.getId()));
      remaining -= TextBudget.bytes(text);
      documentBudget -= TextBudget.bytes(text);
    }
    int count = 0;
    for (var file : files) {
      if (count >= props.getMaxSourceFiles() || remaining < 300) break;
      String path = file.path("path").asText();
      var blob = client.get(base + "/git/blobs/" + file.path("sha").asText());
      if (!"base64".equals(blob.path("encoding").asText())) continue;
      byte[] decoded = Base64.getMimeDecoder().decode(blob.path("content").asText());
      if (decoded.length > props.getMaxFileBytes()) continue;
      String text =
          TextBudget.clip(
              TextBudget.redact(new String(decoded, java.nio.charset.StandardCharsets.UTF_8)),
              Math.min(2200, remaining));
      String url =
          repo.getHtmlUrl()
              + "/blob/"
              + sha
              + "/"
              + Arrays.stream(path.split("/"))
                  .map(GitHubClient::segment)
                  .collect(java.util.stream.Collectors.joining("/"));
      sources.add(new Source("file-" + (++count), "CODE", path, url, text, null));
      remaining -= TextBudget.bytes(text);
    }
    var commitNodes = client.get(base + "/commits?sha=" + sha + "&per_page=30");
    var commits = new ArrayList<Commit>();
    commitNodes.forEach(
        n ->
            commits.add(
                new Commit(
                    n.path("sha").asText(),
                    TextBudget.clip(n.path("commit").path("message").asText(), 300),
                    n.path("commit").path("author").path("date").asText(),
                    n.path("author").path("login").asText("unknown"),
                    n.path("html_url").asText())));
    for (var c : commits.stream().limit(5).toList()) {
      sources.add(
          new Source(
              "commit-" + c.sha().substring(0, 8),
              "COMMIT",
              c.sha().substring(0, 8),
              c.url(),
              TextBudget.redact(c.author() + ": " + c.message()),
              null));
    }
    int prCount = 0;
    try {
      var prs = client.get(base + "/pulls?state=closed&sort=updated&direction=desc&per_page=5");
      prCount = prs.size();
      for (var n : prs)
        sources.add(
            new Source(
                "pr-" + n.path("number").asInt(),
                "PULL_REQUEST",
                "PR #" + n.path("number").asInt(),
                n.path("html_url").asText(),
                TextBudget.clip(
                    TextBudget.redact(n.path("title").asText() + "\n" + n.path("body").asText("")),
                    350),
                null));
    } catch (ApiException e) {
      warnings.add("PR 수집 실패: " + e.getCode());
    }
    if (count < files.size()) warnings.add("파일 수/입력 예산에 따라 주요 파일만 수집했습니다.");
    if (tree.path("truncated").asBoolean()) warnings.add("GitHub가 일부 디렉터리 목록만 반환했습니다.");
    warnings.add("파일 내용은 크기 제한에 따라 일부만 포함될 수 있습니다. 커밋은 최근 최대 30건 표본이며 개인 기여도나 전체 커밋 수가 아닙니다.");
    return new Snapshot(
        sha,
        List.copyOf(sources),
        new Activity(
            commits, commits.size(), prCount, tree.path("truncated").asBoolean(), warnings));
  }

  public boolean eligible(String path, long size) {
    String p = path.toLowerCase(Locale.ROOT);
    if (size <= 0 || size > props.getMaxFileBytes() || p.length() > 500) return false;
    if (Arrays.stream(p.split("/"))
        .anyMatch(
            s ->
                Set.of(
                        "node_modules",
                        "build",
                        "dist",
                        "target",
                        "vendor",
                        ".git",
                        ".next",
                        "coverage",
                        "generated",
                        "__pycache__",
                        ".gradle")
                    .contains(s))) return false;
    if (p.contains("lock.")
        || p.endsWith("-lock.json")
        || p.contains(".min.")
        || p.contains("secret")
        || p.contains("credential")
        || p.matches(".*(?:^|/)\\.env.*")
        || p.endsWith(".pem")
        || p.endsWith(".key")) return false;
    return p.matches(
            ".*\\.(java|kt|js|jsx|ts|tsx|py|go|rs|cs|mlua|lua|sql|gradle|xml|json|yml|yaml|md|properties)$")
        || p.endsWith("dockerfile");
  }

  public static int priority(String path) {
    String p = path.toLowerCase(Locale.ROOT);
    if (p.endsWith("build.gradle")
        || p.endsWith("pom.xml")
        || p.endsWith("package.json")
        || p.endsWith("dockerfile")) return 0;
    if (p.contains("security")
        || p.contains("auth")
        || p.contains("service")
        || p.contains("entity")) return 1;
    if (p.contains("controller")
        || p.contains("application")
        || p.contains("main.")
        || p.contains("router")) return 2;
    if (p.endsWith("readme.md")) return 3;
    if (p.contains("test") || p.contains("spec")) return 6;
    return 4;
  }
}
