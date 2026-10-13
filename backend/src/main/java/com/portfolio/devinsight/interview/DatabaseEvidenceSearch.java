package com.portfolio.devinsight.interview;

import com.portfolio.devinsight.analysis.*;
import com.portfolio.devinsight.common.Json;
import com.portfolio.devinsight.project.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseEvidenceSearch implements EvidenceSearch {
  private final EntityManager em;
  private final ProjectRepository projects;
  private final Json json;

  public DatabaseEvidenceSearch(EntityManager em, ProjectRepository projects, Json json) {
    this.em = em;
    this.projects = projects;
    this.json = json;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Hit> search(String question) {
    return search(question, null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Hit> search(String question, Long projectId) {
    var terms = new LinkedHashSet<String>();
    String normalized = question.toLowerCase(Locale.ROOT);
    if (normalized.contains("인증")) terms.add("security");
    if (normalized.contains("도커")) terms.add("docker");
    if (normalized.contains("멀티플레이")) terms.add("multiplayer");
    Arrays.stream(normalized.split("[^\\p{L}\\p{N}_+#.]+"))
        .map(s -> s.replaceFirst("(에서는|으로|에서|은|는|을|를|이|가|로|의)$", ""))
        .filter(s -> s.length() > 1)
        .limit(8)
        .forEach(terms::add);
    var found = new LinkedHashMap<String, Hit>();
    for (String term : terms) {
      var rows =
          em.createQuery(
                  "select e,p,a from AnalysisEvidence e join Project p on"
                      + " p.latestAnalysisId=e.analysisId join ProjectAnalysis a on"
                      + " a.id=e.analysisId where p.published=true and (:projectId is null or"
                      + " p.id=:projectId) and (locate(:term,lower(e.excerpt))>0 or"
                      + " locate(:term,lower(e.path))>0 or locate(:term,lower(a.resultJson))>0)"
                      + " order by e.id desc",
                  Object[].class)
              .setParameter("term", term)
              .setParameter("projectId", projectId)
              .setMaxResults(5)
              .getResultList();
      add(found, rows);
      if (found.size() >= 8) break;
    }
    if (found.isEmpty()
        && (normalized.contains("강점")
            || normalized.contains("대표")
            || normalized.contains("strength"))) {
      add(
          found,
          em.createQuery(
                  "select e,p,a from AnalysisEvidence e join Project p on"
                      + " p.latestAnalysisId=e.analysisId join ProjectAnalysis a on"
                      + " a.id=e.analysisId where p.published=true and (:projectId is null or"
                      + " p.id=:projectId) order by p.featured desc,e.id desc",
                  Object[].class)
              .setParameter("projectId", projectId)
              .setMaxResults(8)
              .getResultList());
    }
    return found.values().stream().limit(8).toList();
  }

  private void add(Map<String, Hit> found, List<Object[]> rows) {
    for (var row : rows) {
      var e = (AnalysisEvidence) row[0];
      var p = (Project) row[1];
      var analysis = (ProjectAnalysis) row[2];
      var report = json.read(analysis.getResultJson(), ProjectReport.class);
      String context =
          report.claims().stream()
              .filter(c -> c.evidenceIds().contains(e.getSourceKey()))
              .map(c -> c.status() + ": " + c.title() + " — " + c.detail())
              .collect(java.util.stream.Collectors.joining("\n"));
      String text =
          "Analysis (interpretation, not independently verified):\n"
              + TextBudget.clip(context, 350)
              + "\nSource excerpt:\n"
              + TextBudget.clip(e.getExcerpt(), 900);
      String id = "p" + p.getId() + "-" + e.getSourceKey();
      found.putIfAbsent(
          id,
          new Hit(id, p.getId(), e.getAnalysisId(), p.getTitle(), e.getPath(), e.getUrl(), text));
    }
  }

  @Override
  public boolean stillPublic(List<Hit> hits) {
    return hits.stream()
        .allMatch(
            h ->
                projects
                    .findById(h.projectId())
                    .map(
                        p ->
                            p.isPublished()
                                && Objects.equals(p.getLatestAnalysisId(), h.analysisId()))
                    .orElse(false));
  }
}
