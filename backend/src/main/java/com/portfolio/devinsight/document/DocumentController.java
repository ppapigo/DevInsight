package com.portfolio.devinsight.document;

import com.portfolio.devinsight.project.ProjectService;
import com.portfolio.devinsight.security.Access;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DocumentController {
  private final DocumentService service;

  public DocumentController(DocumentService service) {
    this.service = service;
  }

  public record Approval(boolean publicApproved) {}

  @PostMapping("/api/projects/{id}/documents")
  ResponseEntity<ProjectService.DocumentView> upload(
      @PathVariable Long id, @RequestParam("file") MultipartFile file) {
    return ResponseEntity.status(201).body(service.upload(id, file));
  }

  @PatchMapping("/api/documents/{id}/approval")
  ProjectService.DocumentView approve(@PathVariable Long id, @RequestBody Approval request) {
    return service.approval(id, request.publicApproved());
  }

  @GetMapping("/api/documents/{id}/content")
  ResponseEntity<FileSystemResource> download(@PathVariable Long id, Authentication auth) {
    var file = service.download(id, Access.isAdmin(auth));
    var d = file.document();
    String disposition =
        (d.getContentType().startsWith("image/")
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
            .filename(d.getOriginalName(), StandardCharsets.UTF_8)
            .build()
            .toString();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(d.getContentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(new FileSystemResource(file.path()));
  }
}
