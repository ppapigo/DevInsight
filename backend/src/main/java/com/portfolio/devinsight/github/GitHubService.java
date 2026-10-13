package com.portfolio.devinsight.github;

import com.portfolio.devinsight.common.*;
import com.portfolio.devinsight.portfolio.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class GitHubService {
  public record Repo(
      long githubId,
      String owner,
      String name,
      String fullName,
      String description,
      String language,
      int stars,
      boolean fork,
      String url,
      String defaultBranch,
      Instant pushedAt) {}

  public record Profile(
      String login, String name, String bio, String avatarUrl, String url, int publicRepos) {}

  public record Listing(Profile profile, List<Repo> repositories, int page, boolean hasMore) {}

  private final GitHubClient client;
  private final GitHubRepositoryStore store;
  private final DeveloperProfileRepository profiles;
  private final Json json;

  public GitHubService(
      GitHubClient client,
      GitHubRepositoryStore store,
      DeveloperProfileRepository profiles,
      Json json) {
    this.client = client;
    this.store = store;
    this.profiles = profiles;
    this.json = json;
  }

  public Listing list(String username, int page) {
    GitHubClient.validateLogin(username);
    if (page < 1 || page > 100) throw ApiException.badRequest("페이지는 1~100 범위여야 합니다.");
    var p = client.get("/users/" + GitHubClient.segment(username));
    var nodes =
        client.get(
            "/users/"
                + GitHubClient.segment(username)
                + "/repos?type=owner&sort=updated&per_page=30&page="
                + page);
    List<Repo> repos = new ArrayList<>();
    nodes.forEach(
        n -> {
          if (!n.path("private").asBoolean()) repos.add(dto(n));
        });
    return new Listing(profile(p), repos, page, nodes.size() == 30);
  }

  public Repo dto(JsonNode n) {
    return new Repo(
        n.path("id").asLong(),
        n.path("owner").path("login").asText(),
        n.path("name").asText(),
        n.path("full_name").asText(),
        str(n, "description"),
        str(n, "language"),
        n.path("stargazers_count").asInt(),
        n.path("fork").asBoolean(),
        n.path("html_url").asText(),
        n.path("default_branch").asText(),
        instant(n, "pushed_at"));
  }

  public Profile profile(JsonNode p) {
    return new Profile(
        p.path("login").asText(),
        p.path("name").isNull() ? p.path("login").asText() : p.path("name").asText(),
        str(p, "bio"),
        str(p, "avatar_url"),
        str(p, "html_url"),
        p.path("public_repos").asInt());
  }

  @Transactional
  public GitHubRepository importRepo(String owner, String name) {
    GitHubClient.validateLogin(owner);
    GitHubClient.validateRepo(name);
    var node =
        client.get("/repos/" + GitHubClient.segment(owner) + "/" + GitHubClient.segment(name));
    if (node.path("private").asBoolean(true)) throw ApiException.badRequest("MVP는 공개 저장소만 지원합니다.");
    var d = dto(node);
    var r = store.findByGithubId(d.githubId()).orElseGet(GitHubRepository::new);
    r.setGithubId(d.githubId());
    r.setFullName(d.fullName());
    r.setOwnerLogin(d.owner());
    r.setDescription(d.description());
    r.setHtmlUrl(d.url());
    r.setDefaultBranch(d.defaultBranch());
    r.setLanguage(d.language());
    r.setStars(d.stars());
    r.setFork(d.fork());
    r.setPushedAt(d.pushedAt());
    r.setCreatedAt(instant(node, "created_at"));
    r.setLanguagesJson(json.write(client.get("/repos/" + d.fullName() + "/languages")));
    var pn = client.get("/users/" + GitHubClient.segment(d.owner()));
    var pd = profile(pn);
    var p = profiles.findByLoginIgnoreCase(pd.login()).orElseGet(DeveloperProfile::new);
    p.setLogin(pd.login());
    p.setName(pd.name());
    p.setBio(pd.bio());
    p.setAvatarUrl(pd.avatarUrl());
    p.setHtmlUrl(pd.url());
    p.setPublicRepos(pd.publicRepos());
    p.setUpdatedAt(Instant.now());
    profiles.save(p);
    return store.save(r);
  }

  static String str(JsonNode n, String key) {
    return n.path(key).isNull() ? "" : n.path(key).asText("");
  }

  static Instant instant(JsonNode n, String key) {
    String s = n.path(key).asText("");
    return s.isBlank() ? null : Instant.parse(s);
  }
}
