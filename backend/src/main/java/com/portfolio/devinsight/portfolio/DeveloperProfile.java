package com.portfolio.devinsight.portfolio;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "developer_profile")
public class DeveloperProfile {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String login;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String bio;

  @Column(length = 1000)
  private String avatarUrl;

  @Column(length = 1000)
  private String htmlUrl;

  private int publicRepos;
  private Instant updatedAt;
}
