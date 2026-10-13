package com.portfolio.devinsight;

import static org.assertj.core.api.Assertions.*;

import com.portfolio.devinsight.analysis.AiBudget;
import com.portfolio.devinsight.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.url=jdbc:h2:mem:budget;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.ai-calls-per-day=1"
    })
@ActiveProfiles("test")
class AiBudgetIntegrationTest {
  @Autowired AiBudget budget;

  @Test
  void databaseQuotaCannotExceedDailyLimit() {
    budget.reserve();
    assertThatThrownBy(budget::reserve)
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("DAILY_AI_LIMIT"));
    assertThatThrownBy(budget::reserve).isInstanceOf(ApiException.class);
  }
}
