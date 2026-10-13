package com.portfolio.devinsight.interview;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "interview_message")
public class InterviewMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 1000)
  private String question;

  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String answerJson;

  @Column(nullable = false, length = 100)
  private String model;

  private Instant createdAt;
}
