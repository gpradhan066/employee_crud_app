package com.practices.ai.controller.dto;

import java.util.List;

public record SearchResponse(String query, List<SearchResult> results) {
    public record SearchResult(String type, String title, String summary) {
    }
}
