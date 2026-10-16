package com.portfolio.devinsight.project;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
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

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "project_repository", joinColumns = @JoinColumn(name = "project_id"))
  @Column(name = "repository_id", nullable = false, unique = true)
  private Set<Long> repositoryIds = new LinkedHashSet<>();

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  private boolean published;
  private boolean featured;

  @Column(length = 1000)
  private String demoUrl;

  private Long latestAnalysisId;
  // Revision of analysis inputs: approved documents and connected repositories.
  private long documentRevision;
  private Instant createdAt;
}
