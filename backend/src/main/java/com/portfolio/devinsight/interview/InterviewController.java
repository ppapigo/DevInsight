package com.portfolio.devinsight.interview;

import com.portfolio.devinsight.security.RequestLimits;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class InterviewController {
  public record Ask(@NotBlank @Size(max = 1000) String question, @Positive Long projectId) {}

  private final InterviewService service;
  private final RequestLimits limits;

  public InterviewController(InterviewService service, RequestLimits limits) {
    this.service = service;
    this.limits = limits;
  }

  @PostMapping("/api/interview/ask")
  InterviewService.Answer ask(@Valid @RequestBody Ask ask, HttpServletRequest request) {
    limits.interview(request.getRemoteAddr());
    return service.ask(ask.question(), ask.projectId());
  }
}
