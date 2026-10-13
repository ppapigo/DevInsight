package com.portfolio.devinsight.analysis;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "analysis_evidence")
public class AnalysisEvidence {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long analysisId;

  @Column(nullable = false, length = 100)
  private String sourceKey;

  @Column(nullable = false, length = 30)
  private String kind;

  @Column(nullable = false, length = 1000)
  private String path;

  @Column(nullable = false, length = 1500)
  private String url;

  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String excerpt;

  private Long documentId;
}
