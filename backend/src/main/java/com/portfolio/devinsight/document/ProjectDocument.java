package com.portfolio.devinsight.document;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "project_document")
public class ProjectDocument {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long projectId;

  @Column(nullable = false)
  private String originalName;

  @Column(nullable = false, unique = true, length = 100)
  private String storageKey;

  @Column(nullable = false, length = 100)
  private String contentType;

  private long sizeBytes;

  @Column(columnDefinition = "LONGTEXT")
  private String extractedText;

  private boolean publicApproved;

  @Column(nullable = false, length = 64)
  private String sha256;

  private Instant createdAt;
}
