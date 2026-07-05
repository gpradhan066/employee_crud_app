package com.practices.ai.embedding.dto;

import java.util.List;

public record SemanticSearchResponse(String query, List<SemanticSearchResult> results) {
}
