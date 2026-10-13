package com.portfolio.devinsight.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeveloperProfileRepository extends JpaRepository<DeveloperProfile, Long> {
  java.util.Optional<DeveloperProfile> findByLoginIgnoreCase(String login);
}
