package com.portfolio.devinsight;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.portfolio.devinsight.analysis.*;
import com.portfolio.devinsight.common.*;
import com.portfolio.devinsight.document.*;
import com.portfolio.devinsight.github.*;
import com.portfolio.devinsight.interview.*;
import com.portfolio.devinsight.project.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortfolioIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired Json json;
  @Autowired ProjectService projects;
  @Autowired ProjectRepository projectStore;
  @Autowired ProjectAnalysisRepository analysisStore;
  @Autowired AnalysisTransactions tx;
  @Autowired AnalysisWorker worker;
  @Autowired DocumentService documents;
  @Autowired EvidenceSearch search;
  @MockitoBean GitHubClient github;
  @MockitoBean SourceCollector collector;
  @MockitoBean StructuredAi ai;
  private static Long projectId, analysisId, documentId;

  @BeforeEach
  void fixtures() {
    when(github.get("/repos/octocat/sample"))
        .thenReturn(
            json.tree(
                """
                {"id":42,"name":"sample","full_name":"octocat/sample","owner":{"login":"octocat"},"private":false,"description":"Sample project","html_url":"https://github.com/octocat/sample","default_branch":"main","language":"Java","stargazers_count":0,"fork":false,"pushed_at":"2026-10-01T00:00:00Z","created_at":"2026-01-01T00:00:00Z"}
                """));
    when(github.get("/repos/octocat/sample/languages")).thenReturn(json.tree("{\"Java\":5000}"));
    when(github.get("/users/octocat"))
        .thenReturn(
            json.tree(
                "{\"login\":\"octocat\",\"name\":\"Octocat\",\"bio\":\"Developer\",\"avatar_url\":\"https://avatars.githubusercontent.com/u/1\",\"html_url\":\"https://github.com/octocat\",\"public_repos\":1}"));
    when(collector.head(any())).thenReturn("abc123");
    when(collector.collect(any(), anyString(), anyList()))
        .thenReturn(
            new SourceCollector.Snapshot(
                "abc123",
                List.of(
                    new Source(
                        "file-1",
                        "CODE",
                        "SecurityConfig.java",
                        "https://github.com/octocat/sample/blob/abc123/SecurityConfig.java",
                        "Spring Security csrf authentication enabled",
                        null)),
                new SourceCollector.Activity(List.of(), 0, 0, false, List.of("sampled"))));
    when(ai.generate(anyString(), anyString(), anyString(), eq(ProjectReport.class)))
        .thenReturn(
            new ProjectReport(
                "근거 기반 인증 서비스",
                "인증 구현",
                List.of("Spring Security"),
                List.of(
                    new ProjectReport.Claim(
                        ProjectReport.Category.FEATURE,
                        "CSRF 방어",
                        "CSRF를 활성화합니다.",
                        ProjectReport.Status.CONFIRMED,
                        List.of("file-1"))),
                List.of("CSRF를 설명하세요"),
                List.of("기여자 확인 필요")));
  }

  @Test
  @Order(1)
  void publicBootAndSecurityBoundaries() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/api/auth"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.oauthEnabled").value(false))
        .andExpect(jsonPath("$.csrfToken").isNotEmpty());
    mvc.perform(
            post("/api/projects/import")
                .with(csrf())
                .contentType("application/json")
                .content("{\"owner\":\"octocat\",\"name\":\"sample\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/projects/import")
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content("{\"owner\":\"octocat\",\"name\":\"sample\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/github/repos?username=octocat")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/interview/ask")
                .contentType("application/json")
                .content("{\"question\":\"Spring?\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(2)
  void importDefaultsPrivateAndCanPublish() throws Exception {
    var result =
        mvc.perform(
                post("/api/projects/import")
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"owner\":\"octocat\",\"name\":\"sample\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.published").value(false))
            .andReturn();
    projectId = json.tree(result.getResponse().getContentAsString()).path("id").asLong();
    mvc.perform(get("/api/projects/" + projectId)).andExpect(status().isNotFound());
    mvc.perform(get("/api/projects")).andExpect(jsonPath("$.length()").value(0));
    projects.update(projectId, "Sample", "Public source", true, true, "");
    mvc.perform(get("/api/portfolio/overview"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.login").value("octocat"))
        .andExpect(jsonPath("$.projects.length()").value(1));
  }

  @Test
  @Order(3)
  void asyncJobDedupEvidenceAndShaReuse() throws Exception {
    var first = tx.enqueue(projectId, false);
    var same = tx.enqueue(projectId, false);
    assertThat(same.id()).isEqualTo(first.id());
    worker.process(tx.claim().orElseThrow());
    assertThat(tx.get(first.id()).status()).isEqualTo(JobStatus.COMPLETED);
    var view = projects.get(projectId, false);
    analysisId = view.analysis().id();
    assertThat(view.analysis().evidence()).hasSize(1);
    mvc.perform(get("/api/projects/" + projectId))
        .andExpect(jsonPath("$.analysis.report.claims[0].evidenceIds[0]").value("file-1"));
    var reuse = tx.enqueue(projectId, false);
    worker.process(tx.claim().orElseThrow());
    assertThat(tx.get(reuse.id()).message()).contains("재사용");
    assertThat(projects.get(projectId, false).analysis().id()).isEqualTo(analysisId);
    verify(ai, times(1)).generate(anyString(), anyString(), anyString(), eq(ProjectReport.class));
  }

  @Test
  @Order(4)
  void failedReanalysisKeepsPreviousVersion() {
    when(ai.generate(anyString(), anyString(), anyString(), eq(ProjectReport.class)))
        .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "AI_RESPONSE_FAILED", "모의 외부 API 실패"));
    var job = tx.enqueue(projectId, true);
    worker.process(tx.claim().orElseThrow());
    assertThat(tx.get(job.id()).status()).isEqualTo(JobStatus.FAILED);
    assertThat(projects.get(projectId, false).analysis().id()).isEqualTo(analysisId);
  }

  @Test
  @Order(5)
  void privateDocumentsNeverLeakAndRevocationInvalidatesAnalysis() throws Exception {
    var file =
        new MockMultipartFile(
            "file",
            "notes.txt",
            "text/plain",
            "PRIVATE_PHRASE_731 sensitive notes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var result =
        mvc.perform(
                multipart("/api/projects/" + projectId + "/documents")
                    .file(file)
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.publicApproved").value(false))
            .andReturn();
    documentId = json.tree(result.getResponse().getContentAsString()).path("id").asLong();
    mvc.perform(get("/api/documents/" + documentId + "/content")).andExpect(status().isNotFound());
    mvc.perform(get("/api/projects/" + projectId))
        .andExpect(jsonPath("$.documents.length()").value(0));
    assertThat(search.search("PRIVATE_PHRASE_731")).isEmpty();
    assertThat(search.search("Spring Security")).isNotEmpty();
    documents.approval(documentId, true);
    assertThat(projects.get(projectId, false).analysis()).isNull();
    mvc.perform(get("/api/documents/" + documentId + "/content")).andExpect(status().isOk());
    documents.approval(documentId, false);
    mvc.perform(get("/api/documents/" + documentId + "/content")).andExpect(status().isNotFound());
    assertThat(search.search("Spring Security")).isEmpty();
  }

  @Test
  @Order(6)
  void validationAndDangerousUploadAreRejected() throws Exception {
    mvc.perform(
            post("/api/interview/ask")
                .with(csrf())
                .contentType("application/json")
                .content(json.write(Map.of("question", "x".repeat(1001)))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart("/api/projects/" + projectId + "/documents")
                .file(
                    new MockMultipartFile(
                        "file", "../../evil.exe", "application/pdf", "MZ1234".getBytes()))
                .with(user("admin").roles("ADMIN"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    assertThatThrownBy(
            () -> projects.update(projectId, "Sample", "", true, false, "javascript:alert(1)"))
        .isInstanceOf(ApiException.class);
  }

  private void repositoryFixture(long id, String owner, String name) {
    when(github.get("/repos/" + owner + "/" + name)).thenReturn(json.tree(json.write(Map.of(
        "id", id, "name", name, "full_name", owner + "/" + name,
        "owner", Map.of("login", owner), "private", false,
        "html_url", "https://github.com/" + owner + "/" + name,
        "default_branch", "main", "language", "Java"))));
    when(github.get("/repos/" + owner + "/" + name + "/languages"))
        .thenReturn(json.tree("{\"Java\":100}"));
    when(github.get("/users/" + owner)).thenReturn(json.tree(json.write(Map.of(
        "login", owner, "name", owner, "html_url", "https://github.com/" + owner))));
  }

  private ProjectController.ImportRequest repo(String owner, String name) {
    return new ProjectController.ImportRequest(owner, name);
  }

  @Test
  @Order(7)
  void multipleProjectsWithMultipleOwnersAndRepositoriesRemainIndependent() throws Exception {
    repositoryFixture(101, "alice", "frontend");
    repositoryFixture(102, "team", "backend");
    repositoryFixture(103, "bob", "mobile");
    repositoryFixture(104, "team", "api");
    var result = mvc.perform(post("/api/projects")
        .with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
        .content(json.write(Map.of("title", "Team workspace", "repositories",
            List.of(repo("alice", "frontend"), repo("team", "backend"))))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.published").value(false))
        .andExpect(jsonPath("$.repositories.length()").value(2))
        .andExpect(jsonPath("$.languages.Java").value(200))
        .andReturn();
    long firstId = json.tree(result.getResponse().getContentAsString()).path("id").asLong();
    var second = projects.create("Mobile", List.of(repo("bob", "mobile"), repo("team", "api")));
    assertThat(second.id()).isNotEqualTo(firstId);
    assertThat(second.repositories()).extracting(ProjectService.RepositoryView::fullName)
        .containsExactly("bob/mobile", "team/api");
    mvc.perform(get("/api/projects/" + firstId)).andExpect(status().isNotFound());
    assertThat(projects.importProject("team", "backend").id()).isEqualTo(firstId);
    assertThat(projects.get(firstId, true).repositories()).hasSize(2);
    projects.update(firstId, "Team workspace", "", true, false, "");
    mvc.perform(get("/api/projects/" + firstId))
        .andExpect(jsonPath("$.repositories[1].fullName").value("team/backend"));
    mvc.perform(get("/api/projects/" + second.id())).andExpect(status().isNotFound());
  }

  @Test
  @Order(8)
  void allRepositoryHeadsAndNamespacedEvidenceParticipateInAnalysis() {
    repositoryFixture(201, "analysis", "frontend");
    repositoryFixture(202, "analysis", "backend");
    var p = projects.create("Combined analysis", List.of(repo("analysis", "frontend"), repo("analysis", "backend")));
    var first = tx.enqueue(p.id(), false);
    worker.process(tx.claim().orElseThrow());
    assertThat(tx.get(first.id()).status()).isEqualTo(JobStatus.COMPLETED);
    var analysis = projects.get(p.id(), true).analysis();
    assertThat(analysis.commitSha()).hasSize(64);
    assertThat(analysis.evidence()).hasSize(2);
    assertThat(analysis.evidence()).extracting(Source::id).doesNotHaveDuplicates();
    assertThat(analysis.evidence()).extracting(Source::path)
        .containsExactly("analysis/frontend/SecurityConfig.java", "analysis/backend/SecurityConfig.java");
    var reused = tx.enqueue(p.id(), false);
    worker.process(tx.claim().orElseThrow());
    assertThat(tx.get(reused.id()).message()).contains("재사용");
    when(collector.head(argThat(r -> r.getFullName().equals("analysis/backend")))).thenReturn("changed-backend-sha");
    var changed = tx.enqueue(p.id(), false);
    worker.process(tx.claim().orElseThrow());
    assertThat(tx.get(changed.id()).status()).isEqualTo(JobStatus.COMPLETED);
    assertThat(projects.get(p.id(), true).analysis().id()).isNotEqualTo(analysis.id());
    verify(ai, times(2)).generate(anyString(), contains("analysis/frontend, analysis/backend"), anyString(), eq(ProjectReport.class));
  }

  @Test
  @Order(9)
  void connectingRepositoriesInvalidatesAnalysisAndRejectsStaleInFlightResults() throws Exception {
    repositoryFixture(301, "changing", "web");
    repositoryFixture(302, "changing", "api");
    repositoryFixture(303, "changing", "worker");
    var p = projects.importProject("changing", "web");
    tx.enqueue(p.id(), false);
    worker.process(tx.claim().orElseThrow());
    assertThat(projects.get(p.id(), true).analysis()).isNotNull();
    mvc.perform(post("/api/projects/" + p.id() + "/repositories")
        .with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
        .content(json.write(Map.of("repositories", List.of(repo("changing", "api"))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.repositories.length()").value(2))
        .andExpect(jsonPath("$.analysis").isEmpty());
    long revision = projectStore.findById(p.id()).orElseThrow().getDocumentRevision();
    projects.addRepositories(p.id(), List.of(repo("changing", "api")));
    assertThat(projectStore.findById(p.id()).orElseThrow().getDocumentRevision()).isEqualTo(revision);
    when(ai.generate(anyString(), anyString(), anyString(), eq(ProjectReport.class)))
        .thenAnswer(call -> {
          projects.addRepositories(p.id(), List.of(repo("changing", "worker")));
          return new ProjectReport("프로젝트 요약", "프로젝트 목적", List.of(), List.of(), List.of(), List.of());
        });
    var stale = tx.enqueue(p.id(), false);
    worker.process(tx.claim().orElseThrow());
    assertThat(tx.get(stale.id()).status()).isEqualTo(JobStatus.FAILED);
    assertThat(tx.get(stale.id()).errorCode()).isEqualTo("DOCUMENTS_CHANGED");
    assertThat(projects.get(p.id(), true).analysis()).isNull();
    assertThat(projects.get(p.id(), true).repositories()).hasSize(3);
  }

  @Test
  @Order(10)
  void groupedRegistrationIsAtomicAndEnforcesValidationAndSecurity() throws Exception {
    repositoryFixture(401, "atomic", "existing");
    repositoryFixture(402, "atomic", "new");
    var existing = projects.importProject("atomic", "existing");
    long count = projectStore.count();
    mvc.perform(post("/api/projects").with(user("admin").roles("ADMIN")).with(csrf())
        .contentType("application/json").content(json.write(Map.of("title", "Conflict", "repositories",
            List.of(repo("atomic", "new"), repo("atomic", "existing"))))))
        .andExpect(status().isConflict());
    assertThat(projectStore.count()).isEqualTo(count);
    assertThat(projects.get(existing.id(), true).repositories()).hasSize(1);
    // A failed group did not reserve any of its other repositories.
    assertThat(projects.create("Retry", List.of(repo("atomic", "new"))).repositories()).hasSize(1);
    for (String path : List.of("/api/projects", "/api/projects/" + existing.id() + "/repositories")) {
      String body = json.write(Map.of("title", "Invalid", "repositories", List.of()));
      mvc.perform(post(path).with(csrf()).contentType("application/json").content(body))
          .andExpect(status().isUnauthorized());
      mvc.perform(post(path).with(user("admin").roles("ADMIN")).contentType("application/json").content(body))
          .andExpect(status().isForbidden());
      mvc.perform(post(path).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(post("/api/projects").with(user("admin").roles("ADMIN")).with(csrf())
        .contentType("application/json").content(json.write(Map.of("title", "Invalid", "repositories",
            List.of(repo("", "name")))))).andExpect(status().isBadRequest());
    mvc.perform(post("/api/projects").with(user("admin").roles("ADMIN")).with(csrf())
        .contentType("application/json").content(json.write(Map.of("title", "Too many", "repositories",
            Collections.nCopies(11, repo("atomic", "new")))))).andExpect(status().isBadRequest());
  }

  @Test
  @Order(11)
  void repositoryLimitIncludesPreviouslyConnectedRepositories() throws Exception {
    var selected = new ArrayList<ProjectController.ImportRequest>();
    for (int i = 0; i < 11; i++) {
      repositoryFixture(500 + i, "limits", "repo-" + i);
      if (i < 10) selected.add(repo("limits", "repo-" + i));
    }
    var p = projects.create("Full project", selected);
    assertThat(p.repositories()).hasSize(10);
    mvc.perform(post("/api/projects/" + p.id() + "/repositories")
        .with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
        .content(json.write(Map.of("repositories", List.of(repo("limits", "repo-10"))))))
        .andExpect(status().isBadRequest());
    assertThat(projects.get(p.id(), true).repositories()).hasSize(10);
    assertThat(projects.addRepositories(p.id(), List.of(repo("limits", "repo-0"))).repositories()).hasSize(10);
  }

  @Test
  @Order(12)
  void englishCachedReportIsRegeneratedWithoutForcingOrChangingCommits() {
    repositoryFixture(601, "language", "legacy");
    var p = projects.importProject("language", "legacy");
    tx.enqueue(p.id(), false);
    worker.process(tx.claim().orElseThrow());
    var stored = analysisStore.findById(projects.get(p.id(), true).analysis().id()).orElseThrow();
    stored.setResultJson(json.write(new ProjectReport(
        "An authentication service", "Provides authentication", List.of("Spring Security"),
        List.of(), List.of("How is CSRF prevented?"), List.of("Authorship is unverified"))));
    analysisStore.save(stored);

    var job = tx.enqueue(p.id(), false);
    worker.process(tx.claim().orElseThrow());

    assertThat(tx.get(job.id()).status()).isEqualTo(JobStatus.COMPLETED);
    var regenerated = projects.get(p.id(), true).analysis();
    assertThat(regenerated.id()).isNotEqualTo(stored.getId());
    assertThat(regenerated.commitSha()).isEqualTo(stored.getCommitSha());
    assertThat(regenerated.report().summary()).isEqualTo("근거 기반 인증 서비스");
    verify(ai, times(2)).generate(anyString(), anyString(), anyString(), eq(ProjectReport.class));
  }

  @Test
  @Order(13)
  void nonKoreanResponseCannotReplaceExistingAnalysis() {
    repositoryFixture(602, "language", "preserved");
    var p = projects.importProject("language", "preserved");
    tx.enqueue(p.id(), false);
    worker.process(tx.claim().orElseThrow());
    var previous = projects.get(p.id(), true).analysis();
    when(ai.generate(anyString(), anyString(), anyString(), eq(ProjectReport.class)))
        .thenReturn(new ProjectReport("English summary", "English purpose", List.of("React"),
            List.of(), List.of(), List.of()));

    var job = tx.enqueue(p.id(), true);
    worker.process(tx.claim().orElseThrow());

    assertThat(tx.get(job.id()).status()).isEqualTo(JobStatus.FAILED);
    assertThat(tx.get(job.id()).errorCode()).isEqualTo("AI_RESPONSE_LANGUAGE");
    assertThat(tx.get(job.id()).message()).contains("한국어");
    assertThat(projects.get(p.id(), true).analysis().id()).isEqualTo(previous.id());
    verify(ai, times(2)).generate(anyString(), anyString(), anyString(), eq(ProjectReport.class));
  }
}
