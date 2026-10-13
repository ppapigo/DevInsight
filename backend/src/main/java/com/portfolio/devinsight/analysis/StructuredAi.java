package com.portfolio.devinsight.analysis;

public interface StructuredAi {
  void requireConfigured();

  <T> T generate(String system, String input, String schema, Class<T> type);
}
