package com.portfolio.devinsight.analysis;

public record Source(
    String id, String kind, String path, String url, String text, Long documentId) {}
