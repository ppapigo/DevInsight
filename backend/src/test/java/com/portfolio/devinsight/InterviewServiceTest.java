package com.portfolio.devinsight;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.portfolio.devinsight.analysis.*;
import com.portfolio.devinsight.common.Json;
import com.portfolio.devinsight.config.AppProperties;
import com.portfolio.devinsight.interview.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewServiceTest {
  @Test
  void noEvidenceDoesNotInvokeModel() {
    var search = mock(EvidenceSearch.class);
    var ai = mock(StructuredAi.class);
    when(search.search(anyString())).thenReturn(List.of());
    var service =
        new InterviewService(
            search, ai, mock(InterviewMessageRepository.class), new Json(), new AppProperties());
    assertThat(service.ask("unrelated").status())
        .isEqualTo(ProjectReport.Status.INSUFFICIENT_EVIDENCE);
    verify(ai, never()).generate(anyString(), anyString(), anyString(), any());
  }

  @Test
  void filtersFabricatedCitationsAndRevokedSources() {
    var search = mock(EvidenceSearch.class);
    var ai = mock(StructuredAi.class);
    var messages = mock(InterviewMessageRepository.class);
    when(search.search(anyString()))
        .thenReturn(
            List.of(
                new EvidenceSearch.Hit(
                    "p1-file-1",
                    1L,
                    1L,
                    "Sample",
                    "Auth.java",
                    "https://github.com/example/repo/blob/abc/Auth.java",
                    "csrf enabled")));
    when(ai.generate(anyString(), anyString(), anyString(), eq(InterviewService.ModelAnswer.class)))
        .thenReturn(
            new InterviewService.ModelAnswer(
                "Claims", ProjectReport.Status.CONFIRMED, List.of("invented")));
    var service = new InterviewService(search, ai, messages, new Json(), new AppProperties());
    assertThat(service.ask("Spring").sources()).isEmpty();
    when(ai.generate(anyString(), anyString(), anyString(), eq(InterviewService.ModelAnswer.class)))
        .thenReturn(
            new InterviewService.ModelAnswer(
                "Claims", ProjectReport.Status.CONFIRMED, List.of("p1-file-1")));
    when(search.stillPublic(anyList())).thenReturn(false);
    assertThat(service.ask("Spring").status())
        .isEqualTo(ProjectReport.Status.INSUFFICIENT_EVIDENCE);
    verifyNoInteractions(messages);
  }
}
