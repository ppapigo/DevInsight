package com.portfolio.devinsight.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {
  private String frontendUrl = "http://localhost:5173";
  private String adminGithubLogin = "";
  private String githubClientId = "";
  private String githubClientSecret = "";
  private String githubToken = "";
  private String openaiApiKey = "";
  private String openaiModel = "gpt-4o-mini";
  private String uploadDir = "./data/uploads";
  private int maxInputTokens = 18000;
  private int maxOutputTokens = 3500;
  private int maxSourceFiles = 24;
  private int maxFileBytes = 48000;
  private int maxDocumentChars = 40000;
  private int interviewPerHour = 12;
  private int aiCallsPerDay = 150;
  private boolean jobsEnabled = true;

  public boolean oauthEnabled() {
    return !githubClientId.isBlank()
        && !githubClientSecret.isBlank()
        && !adminGithubLogin.isBlank();
  }
}
