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

  @Test
  void koreanReportAllowsTechnicalNamesButChecksEveryProseField() {
    var fields = new ArrayList<>(List.of(
        "Spring Boot 인증 서비스입니다.", "JWT로 사용자 인증을 처리합니다.", "JWT 기반 인증",
        "SecurityConfig.java에서 CSRF 보호를 설정합니다.", "JWT 만료는 어떻게 처리하나요?",
        "개인 기여 범위는 추가 확인이 필요합니다."));
    var korean = localizedReport(fields);
    assertThat(AnalysisPrompts.isKoreanReport(korean)).isTrue();
    assertThatCode(() -> AnalysisPrompts.requireKoreanReport(korean)).doesNotThrowAnyException();
    assertThat(korean.technologies()).containsExactly("Spring Boot", "JWT");
    assertThat(korean.claims().getFirst().evidenceIds()).containsExactly("repo-1-file-1");
    for (int i = 0; i < fields.size(); i++) {
      var mixed = new ArrayList<>(fields);
      mixed.set(i, "An English-only explanation");
      var report = localizedReport(mixed);
      assertThat(AnalysisPrompts.isKoreanReport(report)).as("prose field %s", i).isFalse();
      assertThatThrownBy(() -> AnalysisPrompts.requireKoreanReport(report))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getCode())
          .isEqualTo("AI_RESPONSE_LANGUAGE");
    }
  }

  private ProjectReport localizedReport(List<String> fields) {
    return new ProjectReport(fields.get(0), fields.get(1), List.of("Spring Boot", "JWT"),
        List.of(new ProjectReport.Claim(ProjectReport.Category.FEATURE, fields.get(2), fields.get(3),
            ProjectReport.Status.CONFIRMED, List.of("repo-1-file-1"))),
        List.of(fields.get(4)), List.of(fields.get(5)));
  }
}
