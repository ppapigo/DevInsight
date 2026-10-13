package com.portfolio.devinsight.github;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "github_repository")
public class GitHubRepository {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private Long githubId;

  @Column(nullable = false, unique = true, length = 300)
  private String fullName;

  @Column(nullable = false, length = 100)
  private String ownerLogin;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false, length = 1000)
  private String htmlUrl;

  @Column(nullable = false)
  private String defaultBranch;

  @Column(length = 100)
  private String language;

  private int stars;
  private boolean fork;
  private Instant pushedAt;
  private Instant createdAt;

  @Column(columnDefinition = "TEXT")
  private String languagesJson;
}
