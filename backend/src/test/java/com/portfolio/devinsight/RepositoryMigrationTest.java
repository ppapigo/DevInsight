package com.portfolio.devinsight;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class RepositoryMigrationTest {
  @Test
  void existingProjectsKeepTheirRepositoryWhenUpgrading() throws Exception {
    String url = "jdbc:h2:mem:repository_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
      statement.executeUpdate("""
          INSERT INTO github_repository(id, github_id, full_name, owner_login, html_url, default_branch, stars, fork)
          VALUES (1, 123, 'legacy/project', 'legacy', 'https://github.com/legacy/project', 'main', 0, false)
          """);
      statement.executeUpdate("""
          INSERT INTO project(id, repository_id, title, published, created_at)
          VALUES (1, 1, 'Existing project', true, CURRENT_TIMESTAMP)
          """);
    }
    Flyway.configure().dataSource(url, "sa", "").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement();
        var rows = statement.executeQuery("SELECT p.title, p.published, r.repository_id FROM project p JOIN project_repository r ON r.project_id = p.id")) {
      assertThat(rows.next()).isTrue();
      assertThat(rows.getString(1)).isEqualTo("Existing project");
      assertThat(rows.getBoolean(2)).isTrue();
      assertThat(rows.getLong(3)).isEqualTo(1);
      assertThat(rows.next()).isFalse();
    }
  }
}
