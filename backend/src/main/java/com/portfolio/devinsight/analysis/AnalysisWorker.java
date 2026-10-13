package com.portfolio.devinsight.analysis;

import com.portfolio.devinsight.common.*;
import com.portfolio.devinsight.config.AppProperties;
import com.portfolio.devinsight.document.*;
import com.portfolio.devinsight.github.*;
import com.portfolio.devinsight.project.*;
import java.util.Map;
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
      var repo = repos.findById(project.getRepositoryId()).orElseThrow(ApiException::notFound);
      String sha = collector.head(repo);
      if (!job.isForceRun() && project.getLatestAnalysisId() != null) {
        var previous =
            analyses.findById(project.getLatestAnalysisId()).orElseThrow(ApiException::notFound);
        if (sha.equals(previous.getCommitSha())
            && previous.getDocumentRevision() == project.getDocumentRevision()
            && previous.getModel().equals(props.getOpenaiModel())) {
          tx.unchanged(job.getId(), sha);
          return;
        }
      }
      var snapshot =
          collector.collect(
              repo,
              sha,
              documents.findByProjectIdAndPublicApprovedTrueOrderByIdAsc(project.getId()));
      String schema = AnalysisPrompts.schema(json);
      var selected = new java.util.ArrayList<>(snapshot.sources());
      String input = json.write(Map.of("repository", repo.getFullName(), "sources", selected));
      while (selected.size() > 1
          && TextBudget.bytes(input)
                  + TextBudget.bytes(AnalysisPrompts.SYSTEM)
                  + TextBudget.bytes(schema)
                  + 512
              > props.getMaxInputTokens()) {
        selected.removeLast();
        input = json.write(Map.of("repository", repo.getFullName(), "sources", selected));
      }
      snapshot =
          new SourceCollector.Snapshot(
              snapshot.sha(), java.util.List.copyOf(selected), snapshot.activity());
      var report = ai.generate(AnalysisPrompts.SYSTEM, input, schema, ProjectReport.class);
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
}
