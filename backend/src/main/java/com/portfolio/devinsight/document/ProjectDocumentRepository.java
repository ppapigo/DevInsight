package com.portfolio.devinsight.document;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long> {
  java.util.List<ProjectDocument> findByProjectIdOrderByCreatedAtDesc(Long projectId);

  java.util.List<ProjectDocument> findByProjectIdAndPublicApprovedTrueOrderByIdAsc(Long projectId);

  long countByProjectId(Long projectId);
}
