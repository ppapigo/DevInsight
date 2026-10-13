package com.portfolio.devinsight.portfolio;

import com.portfolio.devinsight.project.ProjectService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PortfolioController {
  public record Profile(
      String login, String name, String bio, String avatarUrl, String githubUrl, int publicRepos) {}

  public record Overview(
      Profile profile,
      List<ProjectService.View> projects,
      Map<String, Long> languages,
      int evidenceCount,
      int analyzedProjects) {}

  private final ProjectService projects;
  private final DeveloperProfileRepository profiles;

  public PortfolioController(ProjectService projects, DeveloperProfileRepository profiles) {
    this.projects = projects;
    this.profiles = profiles;
  }

  @GetMapping("/api/portfolio/overview")
  Overview overview() {
    var list = projects.list(false);
    Profile profile = null;
    if (!list.isEmpty()) {
      String login = list.getFirst().repository().split("/")[0];
      profile =
          profiles
              .findByLoginIgnoreCase(login)
              .map(
                  p ->
                      new Profile(
                          p.getLogin(),
                          p.getName(),
                          p.getBio(),
                          p.getAvatarUrl(),
                          p.getHtmlUrl(),
                          p.getPublicRepos()))
              .orElse(null);
    }
    Map<String, Long> languages = new LinkedHashMap<>();
    list.forEach(p -> p.languages().forEach((k, v) -> languages.merge(k, v, Long::sum)));
    return new Overview(
        profile,
        list,
        languages,
        list.stream()
            .filter(p -> p.analysis() != null)
            .mapToInt(p -> p.analysis().evidence().size())
            .sum(),
        (int) list.stream().filter(p -> p.analysis() != null).count());
  }
}
