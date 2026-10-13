package com.portfolio.devinsight.interview;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InterviewRetention {
  private final JdbcTemplate jdbc;

  public InterviewRetention(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
  public void purge() {
    jdbc.update(
        "DELETE FROM interview_message WHERE created_at < ?",
        java.sql.Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS)));
  }
}
