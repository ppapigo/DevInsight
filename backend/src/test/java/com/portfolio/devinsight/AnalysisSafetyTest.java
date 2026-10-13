package com.portfolio.devinsight;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.portfolio.devinsight.analysis.*;
import com.portfolio.devinsight.common.*;
import com.portfolio.devinsight.config.AppProperties;
import com.portfolio.devinsight.github.*;
import com.portfolio.devinsight.security.RequestLimits;
import java.util.*;
import org.junit.jupiter.api.Test;

class AnalysisSafetyTest {
  @Test
  void rejectsGeneratedFilesSecretsAndOversizedInputs() {
    var c = new SourceCollector(mock(GitHubClient.class), new AppProperties());
    for (String p :
        List.of(
            "node_modules/a.ts",
            "target/Class.java",
            ".env",
            "app/secrets.json",
            "a.min.js",
            "package-lock.json",
            "dist/main.js")) assertThat(c.eligible(p, 100)).as(p).isFalse();
    assertThat(c.eligible("src/main/java/app/SecurityConfig.java", 1200)).isTrue();
    assertThat(c.eligible("src/Main.java", 49000)).isFalse();
    assertThat(SourceCollector.priority("src/AuthService.java"))
        .isLessThan(SourceCollector.priority("README.md"));
  }

  @Test
  void unknownCitationsCannotBePublishedAsConfirmed() {
    var report =
        new ProjectReport(
            "summary",
            "purpose",
            List.of(),
            List.of(
                new ProjectReport.Claim(
                    ProjectReport.Category.FEATURE,
                    "title",
                    "detail",
                    ProjectReport.Status.CONFIRMED,
                    List.of("invented"))),
            List.of(),
            List.of());
    var validated = AnalysisPrompts.validateEvidence(report, List.of());
    assertThat(validated.claims().getFirst().status())
        .isEqualTo(ProjectReport.Status.INSUFFICIENT_EVIDENCE);
    assertThat(validated.claims().getFirst().evidenceIds()).isEmpty();
  }

  @Test
  void utf8BudgetAndRedactionAreBounded() {
    assertThat(TextBudget.bytes(TextBudget.clip("한글😀".repeat(50), 51))).isLessThanOrEqualTo(51);
    assertThat(TextBudget.redact("password: topsecret\napi_key=supersecret\nclass Safe {}"))
        .doesNotContain("topsecret", "supersecret")
        .contains("class Safe");
  }

  @Test
  void missingKeyIsExplicitAndDoesNotReserveBudget() {
    var budget = mock(AiBudget.class);
    var service =
        new OpenAiService(
            new AppProperties(), new Json(), budget, mock(jakarta.validation.Validator.class));
    assertThatThrownBy(service::requireConfigured)
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getCode())
        .isEqualTo("AI_NOT_CONFIGURED");
    verifyNoInteractions(budget);
  }

  @Test
  void publicInterviewHasPerAddressLimit() {
    var p = new AppProperties();
    p.setInterviewPerHour(2);
    var limiter = new RequestLimits(p);
    limiter.interview("127.0.0.1");
    limiter.interview("127.0.0.1");
    assertThatThrownBy(() -> limiter.interview("127.0.0.1")).isInstanceOf(ApiException.class);
    limiter.interview("127.0.0.2");
  }
}
