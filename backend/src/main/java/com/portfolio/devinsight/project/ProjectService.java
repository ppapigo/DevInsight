package com.portfolio.devinsight.project;

import com.portfolio.devinsight.analysis.*;
import com.portfolio.devinsight.common.*;
import com.portfolio.devinsight.document.*;
import com.portfolio.devinsight.github.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
  public record AnalysisView(
      Long id,
      String commitSha,
      String model,
      Instant createdAt,
      int inputBytes,
      ProjectReport report,
      List<Source> evidence,
      SourceCollector.Activity activity) {}

  public record DocumentView(
      Long id,
      String name,
      String contentType,
      long sizeBytes,
      boolean publicApproved,
      Instant createdAt,
      String url) {}

  public record View(
      Long id,
      String title,
      String description,
      boolean published,
      boolean featured,
      String demoUrl,
      String repository,
      String githubUrl,
      String language,
      int stars,
      boolean fork,
      Instant repositoryCreatedAt,
      Instant pushedAt,
      Map<String, Long> languages,
      AnalysisView analysis,
      List<DocumentView> documents) {}

  private final ProjectRepository projects;
  private final GitHubRepositoryStore repos;
  private final GitHubService github;
  private final ProjectAnalysisRepository analyses;
  private final AnalysisEvidenceRepository evidence;
  private final ProjectDocumentRepository documents;
  private final Json json;

  public ProjectService(
      ProjectRepository projects,
      GitHubRepositoryStore repos,
      GitHubService github,
      ProjectAnalysisRepository analyses,
      AnalysisEvidenceRepository evidence,
      ProjectDocumentRepository documents,
      Json json) {
    this.projects = projects;
    this.repos = repos;
    this.github = github;
    this.analyses = analyses;
    this.evidence = evidence;
    this.documents = documents;
    this.json = json;
  }

  @Transactional
  public View importProject(String owner, String name) {
    var repo = github.importRepo(owner, name);
    var p =
        projects
            .findByRepositoryId(repo.getId())
            .orElseGet(
                () -> {
                  var n = new Project();
                  n.setRepositoryId(repo.getId());
                  n.setTitle(name);
                  n.setDescription(repo.getDescription());
                  n.setCreatedAt(Instant.now());
                  return n;
                });
    return view(projects.save(p), true);
  }

  public Project accessible(Long id, boolean admin) {
    var p = projects.findById(id).orElseThrow(ApiException::notFound);
    if (!admin && !p.isPublished()) throw ApiException.notFound();
    return p;
  }

  @Transactional(readOnly = true)
  public List<View> list(boolean admin) {
    return (admin
            ? projects.findAllByOrderByIdDesc()
            : projects.findByPublishedTrueOrderByFeaturedDescIdDesc())
        .stream().map(p -> view(p, admin)).toList();
  }

  @Transactional(readOnly = true)
  public View get(Long id, boolean admin) {
    return view(accessible(id, admin), admin);
  }

  @Transactional
  public View update(
      Long id,
      String title,
      String description,
      boolean published,
      boolean featured,
      String demoUrl) {
    var p = projects.lockById(id).orElseThrow(ApiException::notFound);
    if (demoUrl != null && !demoUrl.isBlank()) {
      try {
        URI u = URI.create(demoUrl);
        if (!"https".equalsIgnoreCase(u.getScheme())
            || u.getHost() == null
            || u.getUserInfo() != null) throw new IllegalArgumentException();
      } catch (IllegalArgumentException e) {
        throw ApiException.badRequest("시연 링크는 유효한 HTTPS 주소여야 합니다.");
      }
    }
    p.setTitle(title);
    p.setDescription(description);
    p.setPublished(published);
    p.setFeatured(featured);
    p.setDemoUrl(demoUrl);
    return view(projects.save(p), true);
  }

  public View view(Project p, boolean admin) {
    var r = repos.findById(p.getRepositoryId()).orElseThrow(ApiException::notFound);
    AnalysisView a = null;
    if (p.getLatestAnalysisId() != null) {
      var n = analyses.findById(p.getLatestAnalysisId()).orElseThrow(ApiException::notFound);
      a =
          new AnalysisView(
              n.getId(),
              n.getCommitSha(),
              n.getModel(),
              n.getCreatedAt(),
              n.getInputTokens(),
              json.read(n.getResultJson(), ProjectReport.class),
              evidence.findByAnalysisIdOrderByIdAsc(n.getId()).stream()
                  .map(
                      e ->
                          new Source(
                              e.getSourceKey(),
                              e.getKind(),
                              e.getPath(),
                              e.getUrl(),
                              e.getExcerpt(),
                              e.getDocumentId()))
                  .toList(),
              json.read(n.getActivityJson(), SourceCollector.Activity.class));
    }
    var docs =
        documents.findByProjectIdOrderByCreatedAtDesc(p.getId()).stream()
            .filter(d -> admin || d.isPublicApproved())
            .map(ProjectService::documentView)
            .toList();
    Map<String, Long> languages = new LinkedHashMap<>();
    if (r.getLanguagesJson() != null)
      json.tree(r.getLanguagesJson())
          .properties()
          .forEach(e -> languages.put(e.getKey(), e.getValue().asLong()));
    return new View(
        p.getId(),
        p.getTitle(),
        p.getDescription(),
        p.isPublished(),
        p.isFeatured(),
        p.getDemoUrl(),
        r.getFullName(),
        r.getHtmlUrl(),
        r.getLanguage(),
        r.getStars(),
        r.isFork(),
        r.getCreatedAt(),
        r.getPushedAt(),
        languages,
        a,
        docs);
  }

  public static DocumentView documentView(ProjectDocument d) {
    return new DocumentView(
        d.getId(),
        d.getOriginalName(),
        d.getContentType(),
        d.getSizeBytes(),
        d.isPublicApproved(),
        d.getCreatedAt(),
        "/api/documents/" + d.getId() + "/content");
  }
}
