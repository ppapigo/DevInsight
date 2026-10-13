package com.portfolio.devinsight.github;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubRepositoryStore extends JpaRepository<GitHubRepository, Long> {
  java.util.Optional<GitHubRepository> findByGithubId(Long githubId);
}
