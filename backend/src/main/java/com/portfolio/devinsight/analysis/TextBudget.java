package com.portfolio.devinsight.analysis;

import java.nio.charset.StandardCharsets;

public final class TextBudget {
  private TextBudget() {}

  public static int bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }

  public static String clip(String value, int maxBytes) {
    if (maxBytes <= 0) return "";
    if (bytes(value) <= maxBytes) return value;
    int lo = 0, hi = value.length();
    while (lo < hi) {
      int mid = (lo + hi + 1) / 2;
      if (bytes(value.substring(0, mid)) <= maxBytes) lo = mid;
      else hi = mid - 1;
    }
    if (lo > 0 && Character.isHighSurrogate(value.charAt(lo - 1))) lo--;
    return value.substring(0, lo);
  }

  public static String redact(String text) {
    return text.replaceAll(
            "(?im)((?:password|secret|api[_-]?key|access[_-]?token|authorization)\\s*[:=]\\s*)[^\\r"
                + "\\n"
                + "]+",
            "$1[REDACTED]")
        .replaceAll(
            "(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,})",
            "[REDACTED]");
  }
}
