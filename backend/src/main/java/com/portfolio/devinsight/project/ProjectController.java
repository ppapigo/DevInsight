package com.portfolio.devinsight.project;

import com.portfolio.devinsight.security.Access;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProjectController {
  public record ImportRequest(
      @NotBlank @Size(max = 39) String owner, @NotBlank @Size(max = 100) String name) {}

  public record UpdateRequest(
      @NotBlank @Size(max = 255) String title,
      @Size(max = 3000) String description,
      boolean published,
      boolean featured,
      @Size(max = 1000) String demoUrl) {}

  private final ProjectService service;

  public ProjectController(ProjectService service) {
    this.service = service;
  }

  @PostMapping("/projects/import")
  ResponseEntity<ProjectService.View> importProject(@Valid @RequestBody ImportRequest request) {
    return ResponseEntity.status(201).body(service.importProject(request.owner(), request.name()));
  }

  @GetMapping("/projects")
  List<ProjectService.View> list() {
    return service.list(false);
  }

  @GetMapping("/admin/projects")
  List<ProjectService.View> adminList() {
    return service.list(true);
  }

  @GetMapping("/projects/{id}")
  ProjectService.View get(@PathVariable Long id, Authentication auth) {
    return service.get(id, Access.isAdmin(auth));
  }

  @PatchMapping("/projects/{id}")
  ProjectService.View update(@PathVariable Long id, @Valid @RequestBody UpdateRequest r) {
    return service.update(id, r.title(), r.description(), r.published(), r.featured(), r.demoUrl());
  }
}
