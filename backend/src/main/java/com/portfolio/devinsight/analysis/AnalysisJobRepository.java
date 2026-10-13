package com.portfolio.devinsight.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
  java.util.Optional<AnalysisJob> findByActiveProjectId(Long projectId);

  java.util.Optional<AnalysisJob> findFirstByStatusOrderByRequestedAtAsc(JobStatus status);

  java.util.List<AnalysisJob> findByStatusAndStartedAtBefore(
      JobStatus status, java.time.Instant before);
}
