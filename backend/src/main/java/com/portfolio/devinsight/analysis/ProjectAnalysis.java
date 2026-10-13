package com.portfolio.devinsight.analysis;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "project_analysis")
public class ProjectAnalysis {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long projectId;

  @Column(nullable = false, length = 64)
  private String commitSha;

  @Column(nullable = false, length = 100)
  private String model;

  private long documentRevision;

  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String resultJson;

  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String activityJson;

  private int inputTokens;
  private Instant createdAt;
}
