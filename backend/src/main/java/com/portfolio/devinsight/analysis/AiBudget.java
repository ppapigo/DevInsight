package com.portfolio.devinsight.analysis;

import com.portfolio.devinsight.common.ApiException;
import com.portfolio.devinsight.config.AppProperties;
import java.time.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiBudget {
  private final JdbcTemplate jdbc;
  private final AppProperties props;

  public AiBudget(JdbcTemplate jdbc, AppProperties props) {
    this.jdbc = jdbc;
    this.props = props;
  }

  @Transactional
  public void reserve() {
    var day = java.sql.Date.valueOf(LocalDate.now(ZoneOffset.UTC));
    jdbc.update(
        "INSERT INTO ai_daily_usage(usage_day,request_count) VALUES (?,0) ON DUPLICATE KEY UPDATE"
            + " usage_day=usage_day",
        day);
    if (jdbc.update(
            "UPDATE ai_daily_usage SET request_count=request_count+1 WHERE usage_day=? AND"
                + " request_count<?",
            day,
            props.getAiCallsPerDay())
        != 1)
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "DAILY_AI_LIMIT", "서비스의 일일 AI 사용 한도에 도달했습니다.", 3600L);
  }
}
