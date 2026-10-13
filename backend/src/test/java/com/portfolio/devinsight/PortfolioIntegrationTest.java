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
}
