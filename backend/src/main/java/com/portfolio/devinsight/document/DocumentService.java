package com.portfolio.devinsight.document;

import com.portfolio.devinsight.analysis.TextBudget;
import com.portfolio.devinsight.common.*;
import com.portfolio.devinsight.config.AppProperties;
import com.portfolio.devinsight.project.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
  public record Download(ProjectDocument document, Path path) {}

  private final ProjectRepository projects;
  private final ProjectDocumentRepository documents;
  private final DocumentExtractor extractor;
  private final Path root;
  private final TransactionTemplate tx;

  public DocumentService(
      ProjectRepository projects,
      ProjectDocumentRepository documents,
      DocumentExtractor extractor,
      AppProperties props,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this.projects = projects;
    this.documents = documents;
    this.extractor = extractor;
    this.root = Path.of(props.getUploadDir()).toAbsolutePath().normalize();
    this.tx = new TransactionTemplate(manager);
  }

  public ProjectService.DocumentView upload(Long projectId, MultipartFile file) {
    projects.findById(projectId).orElseThrow(ApiException::notFound);
    String original = Objects.requireNonNullElse(file.getOriginalFilename(), "document");
    if (original.length() > 200
        || original.contains("/")
        || original.contains("\\")
        || original.chars().anyMatch(c -> c < 32)) throw ApiException.badRequest("파일 이름을 확인해 주세요.");
    Path target = null;
    try {
      byte[] data = file.getBytes();
      var extracted = extractor.extract(original, data);
      String key = UUID.randomUUID() + "." + extracted.extension();
      target = root.resolve(key).normalize();
      if (!target.startsWith(root)) throw ApiException.badRequest("잘못된 저장 경로입니다.");
      Files.createDirectories(root);
      Files.write(target, data, StandardOpenOption.CREATE_NEW);
      var d = new ProjectDocument();
      d.setProjectId(projectId);
      d.setOriginalName(original);
      d.setStorageKey(key);
      d.setContentType(extracted.contentType());
      d.setSizeBytes(data.length);
      d.setExtractedText(TextBudget.redact(extracted.text()));
      d.setPublicApproved(false);
      d.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
      d.setCreatedAt(Instant.now());
      return tx.execute(
          status -> {
            projects.lockById(projectId).orElseThrow(ApiException::notFound);
            if (documents.countByProjectId(projectId) >= 30)
              throw ApiException.badRequest("프로젝트당 최대 30개 자료를 등록할 수 있습니다.");
            return ProjectService.documentView(documents.save(d));
          });
    } catch (Exception e) {
      if (target != null)
        try {
          Files.deleteIfExists(target);
        } catch (java.io.IOException ignored) {
        }
      if (e instanceof ApiException a) throw a;
      throw new ApiException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          "UPLOAD_FAILED",
          "파일을 저장하지 못했습니다.");
    }
  }

  public ProjectService.DocumentView approval(Long id, boolean approved) {
    return tx.execute(
        status -> {
          var d = documents.findById(id).orElseThrow(ApiException::notFound);
          var p = projects.lockById(d.getProjectId()).orElseThrow(ApiException::notFound);
          if (d.isPublicApproved() != approved) {
            d.setPublicApproved(approved);
            documents.save(d);
            p.setDocumentRevision(p.getDocumentRevision() + 1);
            p.setLatestAnalysisId(null);
            projects.save(p);
          }
          return ProjectService.documentView(d);
        });
  }

  public Download download(Long id, boolean admin) {
    var d = documents.findById(id).orElseThrow(ApiException::notFound);
    var p = projects.findById(d.getProjectId()).orElseThrow(ApiException::notFound);
    if (!admin && (!d.isPublicApproved() || !p.isPublished())) throw ApiException.notFound();
    Path path = root.resolve(d.getStorageKey()).normalize();
    if (!path.startsWith(root) || !Files.isRegularFile(path)) throw ApiException.notFound();
    return new Download(d, path);
  }
}
