package com.portfolio.devinsight.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisEvidenceRepository extends JpaRepository<AnalysisEvidence, Long> {
  java.util.List<AnalysisEvidence> findByAnalysisIdOrderByIdAsc(Long analysisId);
}
