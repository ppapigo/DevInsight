package com.portfolio.devinsight.analysis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ProjectReport(
    @NotBlank @Size(max = 500) String summary,
    @NotBlank @Size(max = 1200) String purpose,
    @NotNull @Size(max = 20) List<@Size(max = 60) String> technologies,
    @NotNull @Size(max = 25) List<@Valid Claim> claims,
    @NotNull @Size(max = 8) List<@Size(max = 300) String> interviewQuestions,
    @NotNull @Size(max = 12) List<@Size(max = 400) String> limitations) {
  public enum Status {
    CONFIRMED,
    NEEDS_VERIFICATION,
    INSUFFICIENT_EVIDENCE
  }

  public enum Category {
    FEATURE,
    ARCHITECTURE,
    TECHNOLOGY_CHOICE,
    PROBLEM_SOLVING,
    CONTRIBUTION,
    OPTIMIZATION,
    INTERVIEW_POINT
  }

  public record Claim(
      @NotNull Category category,
      @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 1600) String detail,
      @NotNull Status status,
      @NotNull @Size(max = 8) List<@Size(max = 100) String> evidenceIds) {}
}
