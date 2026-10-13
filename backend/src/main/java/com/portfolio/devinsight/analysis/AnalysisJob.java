package com.portfolio.devinsight.analysis;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "analysis_job")
public class AnalysisJob {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long projectId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private JobStatus status;

  @Column(unique = true)
  private Long activeProjectId;

  private boolean forceRun;
  private Instant requestedAt;
  private Instant startedAt;
  private Instant finishedAt;

  @Column(length = 80)
  private String errorCode;

  @Column(length = 1000)
  private String message;

  @Column(length = 64)
  private String commitSha;
}
