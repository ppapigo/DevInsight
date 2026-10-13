package com.portfolio.devinsight.github;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/github")
public class GitHubController {
  private final GitHubService service;

  public GitHubController(GitHubService service) {
    this.service = service;
  }

  @GetMapping("/repos")
  GitHubService.Listing list(
      @RequestParam String username, @RequestParam(defaultValue = "1") int page) {
    return service.list(username, page);
  }
}
