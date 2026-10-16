package com.portfolio.devinsight.analysis;

import com.portfolio.devinsight.common.*;
import com.portfolio.devinsight.config.AppProperties;
import com.portfolio.devinsight.document.*;
import com.portfolio.devinsight.github.*;
import com.portfolio.devinsight.project.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnalysisWorker {
  private final AnalysisTransactions tx;
  private final ProjectRepository projects;
  private final GitHubRepositoryStore repos;
  private final ProjectAnalysisRepository analyses;
  private final ProjectDocumentRepository documents;
  private final SourceCollector collector;
  private final StructuredAi ai;
  private final AppProperties props;
  private final Json json;
  private volatile boolean ready;

  public AnalysisWorker(
      AnalysisTransactions tx,
      ProjectRepository projects,
      GitHubRepositoryStore repos,
      ProjectAnalysisRepository analyses,
      ProjectDocumentRepository documents,
      SourceCollector collector,
      StructuredAi ai,
      AppProperties props,
      Json json) {
    this.tx = tx;
    this.projects = projects;
    this.repos = repos;
    this.analyses = analyses;
    this.documents = documents;
    this.collector = collector;
    this.ai = ai;
    this.props = props;
    this.json = json;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void ready() {
    if (props.isJobsEnabled()) tx.recover();
    ready = true;
  }

  @Scheduled(fixedDelay = 2000)
  public void tick() {
    if (ready && props.isJobsEnabled()) tx.claim().ifPresent(this::process);
  }

  public void process(AnalysisJob job) {
    try {
      ai.requireConfigured();
      var project = projects.findById(job.getProjectId()).orElseThrow(ApiException::notFound);
      var connected = new ArrayList<>(repos.findAllById(project.getRepositoryIds()));
      connected.sort(Comparator.comparing(GitHubRepository::getId));
      if (connected.isEmpty()) throw ApiException.badRequest("연결된 저장소가 없습니다.");
      var heads = new LinkedHashMap<Long, String>();
      for (var repo : connected) heads.put(repo.getId(), collector.head(repo));
      String sha =
          connected.size() == 1
              ? heads.values().iterator().next()
              : HexFormat.of()
                  .formatHex(
                      MessageDigest.getInstance("SHA-256")
                          .digest(json.write(heads).getBytes(StandardCharsets.UTF_8)));
      if (!job.isForceRun() && project.getLatestAnalysisId() != null) {
        var previous =
            analyses.findById(project.getLatestAnalysisId()).orElseThrow(ApiException::notFound);
        if (sha.equals(previous.getCommitSha())
            && previous.getDocumentRevision() == project.getDocumentRevision()
            && previous.getModel().equals(props.getOpenaiModel())
            && AnalysisPrompts.isKoreanReport(json.read(previous.getResultJson(), ProjectReport.class))) {
          tx.unchanged(job.getId(), sha, project.getDocumentRevision());
          return;
        }
      }
      var approved = documents.findByProjectIdAndPublicApprovedTrueOrderByIdAsc(project.getId());
      var snapshots = new ArrayList<SourceCollector.Snapshot>();
      for (int i = 0; i < connected.size(); i++) {
        var repo = connected.get(i);
        snapshots.add(collector.collect(repo, heads.get(repo.getId()), i == 0 ? approved : List.of()));
      }
      var snapshot = connected.size() == 1 ? snapshots.getFirst() : combine(sha, connected, snapshots);
      String repositoryNames =
          String.join(", ", connected.stream().map(GitHubRepository::getFullName).toList());
      String schema = AnalysisPrompts.schema(json);
      var selected = new java.util.ArrayList<>(snapshot.sources());
      String input = json.write(Map.of("repository", repositoryNames, "sources", selected));
      while (selected.size() > 1
          && TextBudget.bytes(input)
                  + TextBudget.bytes(AnalysisPrompts.SYSTEM)
                  + TextBudget.bytes(schema)
                  + 512
              > props.getMaxInputTokens()) {
        selected.removeLast();
        input = json.write(Map.of("repository", repositoryNames, "sources", selected));
      }
      snapshot =
          new SourceCollector.Snapshot(
              snapshot.sha(), java.util.List.copyOf(selected), snapshot.activity());
      var report = ai.generate(AnalysisPrompts.SYSTEM, input, schema, ProjectReport.class);
      AnalysisPrompts.requireKoreanReport(report);
      tx.complete(
          job.getId(),
          snapshot,
          AnalysisPrompts.validateEvidence(report, snapshot.sources()),
          project.getDocumentRevision(),
          TextBudget.bytes(input));
    } catch (ApiException e) {
      tx.fail(job.getId(), e.getCode(), e.getMessage());
    } catch (Exception e) {
      log.warn("Analysis failed jobId={} type={}", job.getId(), e.getClass().getSimpleName());
      tx.fail(job.getId(), "ANALYSIS_FAILED", "분석에 실패했습니다. 기존 분석을 유지하며 다시 실행할 수 있습니다.");
    }
  }

  private SourceCollector.Snapshot combine(
      String sha,
      List<GitHubRepository> connected,
      List<SourceCollector.Snapshot> snapshots) {
    var sources = new ArrayList<Source>();
    var commits = new ArrayList<SourceCollector.Commit>();
    var warnings = new ArrayList<String>();
    int sampledCommits = 0, sampledPullRequests = 0;
    boolean truncated = false;
    // Interleave repositories so the shared AI budget is not consumed by the first repository.
    int longest = snapshots.stream().mapToInt(s -> s.sources().size()).max().orElse(0);
    int textLimit = Math.max(200, props.getMaxInputTokens() / (connected.size() * 6));
    for (int row = 0; row < longest; row++) {
      for (int i = 0; i < snapshots.size(); i++) {
        if (row >= snapshots.get(i).sources().size()) continue;
        var source = snapshots.get(i).sources().get(row);
        var repo = connected.get(i);
        sources.add(
            new Source(
                source.documentId() == null ? "repo-" + repo.getId() + "-" + source.id() : source.id(),
                source.kind(),
                source.documentId() == null ? repo.getFullName() + "/" + source.path() : source.path(),
                source.url(),
                TextBudget.clip(source.text(), textLimit),
                source.documentId()));
      }
    }
    for (int i = 0; i < snapshots.size(); i++) {
      var activity = snapshots.get(i).activity();
      commits.addAll(activity.commits());
      sampledCommits += activity.sampledCommits();
      sampledPullRequests += activity.sampledPullRequests();
      truncated |= activity.treeTruncated();
      for (var warning : activity.warnings())
        warnings.add(connected.get(i).getFullName() + ": " + warning);
    }
    commits.sort(Comparator.comparing(SourceCollector.Commit::date).reversed());
    warnings.add("여러 저장소의 근거를 공통 입력 예산 안에서 선별했습니다. 분석 ID는 전체 저장소 커밋의 조합입니다.");
    return new SourceCollector.Snapshot(
        sha,
        List.copyOf(sources),
        new SourceCollector.Activity(commits, sampledCommits, sampledPullRequests, truncated, warnings));
  }
}
