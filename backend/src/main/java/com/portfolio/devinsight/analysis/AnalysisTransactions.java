package com.portfolio.devinsight.analysis;

import com.portfolio.devinsight.common.*;
import com.portfolio.devinsight.config.AppProperties;
import com.portfolio.devinsight.github.SourceCollector;
import com.portfolio.devinsight.project.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisTransactions {
  public record JobView(
      Long id,
      Long projectId,
      JobStatus status,
      Instant requestedAt,
      Instant startedAt,
      Instant finishedAt,
      String errorCode,
      String message,
      String commitSha) {}

  private final AnalysisJobRepository jobs;
  private final ProjectRepository projects;
  private final ProjectAnalysisRepository analyses;
  private final AnalysisEvidenceRepository evidence;
  private final Json json;
  private final AppProperties props;

  public AnalysisTransactions(
      AnalysisJobRepository jobs,
      ProjectRepository projects,
      ProjectAnalysisRepository analyses,
      AnalysisEvidenceRepository evidence,
      Json json,
      AppProperties props) {
    this.jobs = jobs;
    this.projects = projects;
    this.analyses = analyses;
    this.evidence = evidence;
    this.json = json;
    this.props = props;
  }

  @Transactional
  public JobView enqueue(Long projectId, boolean force) {
    projects.lockById(projectId).orElseThrow(ApiException::notFound);
    var active = jobs.findByActiveProjectId(projectId);
    if (active.isPresent()) return view(active.get());
    var job = new AnalysisJob();
    job.setProjectId(projectId);
    job.setActiveProjectId(projectId);
    job.setStatus(JobStatus.PENDING);
    job.setForceRun(force);
    job.setRequestedAt(Instant.now());
    job.setMessage("분석 대기 중");
    return view(jobs.save(job));
  }

  @Transactional
  public Optional<AnalysisJob> claim() {
    var next = jobs.findFirstByStatusOrderByRequestedAtAsc(JobStatus.PENDING);
    if (next.isEmpty()) return next;
    var job = next.get();
    job.setStatus(JobStatus.RUNNING);
    job.setStartedAt(Instant.now());
    job.setMessage("공개 코드와 승인된 자료를 분석 중입니다.");
    jobs.save(job);
    return Optional.of(job);
  }

  @Transactional
  public void complete(
      Long jobId,
      SourceCollector.Snapshot snapshot,
      ProjectReport report,
      long documentRevision,
      int inputTokens) {
    var job = jobs.findById(jobId).orElseThrow(ApiException::notFound);
    var project = projects.lockById(job.getProjectId()).orElseThrow(ApiException::notFound);
    if (project.getDocumentRevision() != documentRevision)
      throw new ApiException(
          HttpStatus.CONFLICT, "DOCUMENTS_CHANGED", "분석 중 연결된 저장소 또는 자료의 공개 설정이 변경되었습니다. 다시 분석해 주세요.");
    var analysis = new ProjectAnalysis();
    analysis.setProjectId(project.getId());
    analysis.setCommitSha(snapshot.sha());
    analysis.setModel(props.getOpenaiModel());
    analysis.setDocumentRevision(documentRevision);
    analysis.setResultJson(json.write(report));
    analysis.setActivityJson(json.write(snapshot.activity()));
    analysis.setInputTokens(inputTokens);
    analysis.setCreatedAt(Instant.now());
    analyses.save(analysis);
    for (var source : snapshot.sources()) {
      var e = new AnalysisEvidence();
      e.setAnalysisId(analysis.getId());
      e.setSourceKey(source.id());
      e.setKind(source.kind());
      e.setPath(source.path());
      e.setUrl(source.url());
      e.setExcerpt(source.text());
      e.setDocumentId(source.documentId());
      evidence.save(e);
    }
    project.setLatestAnalysisId(analysis.getId());
    projects.save(project);
    finish(job, JobStatus.COMPLETED, null, "분석 완료", snapshot.sha());
  }

  @Transactional
  public void unchanged(Long id, String sha, long revision) {
    var job = jobs.findById(id).orElseThrow(ApiException::notFound);
    var project = projects.lockById(job.getProjectId()).orElseThrow(ApiException::notFound);
    if (project.getDocumentRevision() != revision || project.getLatestAnalysisId() == null)
      throw new ApiException(HttpStatus.CONFLICT, "DOCUMENTS_CHANGED",
          "분석 중 연결된 저장소 또는 자료가 변경되었습니다. 다시 분석해 주세요.");
    finish(
        job,
        JobStatus.COMPLETED,
        null,
        "커밋과 승인된 자료가 동일하여 기존 분석을 재사용했습니다.",
        sha);
  }

  @Transactional
  public void fail(Long id, String code, String message) {
    jobs.findById(id).ifPresent(j -> finish(j, JobStatus.FAILED, code, message, null));
  }

  @Transactional
  public void recover() {
    jobs.findByStatusAndStartedAtBefore(JobStatus.RUNNING, Instant.now())
        .forEach(
            j ->
                finish(
                    j,
                    JobStatus.FAILED,
                    "WORKER_RESTARTED",
                    "서버 재시작으로 분석이 중단되었습니다. 다시 실행할 수 있습니다.",
                    null));
  }

  private void finish(AnalysisJob job, JobStatus status, String code, String message, String sha) {
    job.setStatus(status);
    job.setActiveProjectId(null);
    job.setErrorCode(code);
    job.setMessage(message);
    job.setCommitSha(sha);
    job.setFinishedAt(Instant.now());
    jobs.save(job);
  }

  public JobView get(Long id) {
    return view(jobs.findById(id).orElseThrow(ApiException::notFound));
  }

  public static JobView view(AnalysisJob j) {
    return new JobView(
        j.getId(),
        j.getProjectId(),
        j.getStatus(),
        j.getRequestedAt(),
        j.getStartedAt(),
        j.getFinishedAt(),
        j.getErrorCode(),
        j.getMessage(),
        j.getCommitSha());
  }
}
