package com.practices.ai.embedding.dto;

public record SemanticSearchResult(String type, Long referenceId, String title, String snippet, Double score) {
}
