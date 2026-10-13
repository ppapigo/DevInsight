package com.portfolio.devinsight.analysis;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class AnalysisController {
  private final AnalysisTransactions tx;
  private final StructuredAi ai;

  public AnalysisController(AnalysisTransactions tx, StructuredAi ai) {
    this.tx = tx;
    this.ai = ai;
  }

  @PostMapping("/api/projects/{id}/analyze")
  ResponseEntity<AnalysisTransactions.JobView> analyze(
      @PathVariable Long id, @RequestParam(defaultValue = "false") boolean force) {
    ai.requireConfigured();
    return ResponseEntity.accepted().body(tx.enqueue(id, force));
  }

  @GetMapping("/api/analysis/jobs/{id}")
  AnalysisTransactions.JobView job(@PathVariable Long id) {
    return tx.get(id);
  }
}
