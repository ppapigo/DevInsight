package com.portfolio.devinsight.project;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
  java.util.List<Project> findByPublishedTrueOrderByFeaturedDescIdDesc();

  java.util.List<Project> findAllByOrderByIdDesc();

  java.util.Optional<Project> findByRepositoryId(Long repositoryId);

  @org.springframework.data.jpa.repository.Query(
      "select p from Project p join p.repositoryIds r where r = :repositoryId")
  java.util.Optional<Project> findByLinkedRepositoryId(
      @org.springframework.data.repository.query.Param("repositoryId") Long repositoryId);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select p from Project p where p.id = :id")
  java.util.Optional<Project> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
