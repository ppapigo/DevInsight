package com.portfolio.devinsight.interview;

import java.util.List;

public interface EvidenceSearch {
  record Hit(
      String id,
      Long projectId,
      Long analysisId,
      String projectTitle,
      String path,
      String url,
      String text) {}

  List<Hit> search(String question);

  default List<Hit> search(String question, Long projectId) {
    return search(question).stream().filter(hit -> hit.projectId().equals(projectId)).toList();
  }

  boolean stillPublic(List<Hit> hits);
}
