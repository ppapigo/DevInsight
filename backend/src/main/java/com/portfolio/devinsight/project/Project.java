package com.portfolio.devinsight.project;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "project")
public class Project {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private Long repositoryId;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  private boolean published;
  private boolean featured;

  @Column(length = 1000)
  private String demoUrl;

  private Long latestAnalysisId;
  private long documentRevision;
  private Instant createdAt;
}
