package com.portfolio.devinsight.security;

import com.github.benmanes.caffeine.cache.*;
import com.portfolio.devinsight.common.ApiException;
import com.portfolio.devinsight.config.AppProperties;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RequestLimits {
  private final Cache<String, AtomicInteger> requests =
      Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofHours(1)).build();
  private final AppProperties props;

  public RequestLimits(AppProperties props) {
    this.props = props;
  }

  public void interview(String remoteAddress) {
    if (requests.get(remoteAddress, k -> new AtomicInteger()).incrementAndGet()
        > props.getInterviewPerHour()) {
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS,
          "QUESTION_LIMIT",
          "시간당 질문 한도를 초과했습니다. 나중에 다시 시도해 주세요.",
          3600L);
    }
  }
}
