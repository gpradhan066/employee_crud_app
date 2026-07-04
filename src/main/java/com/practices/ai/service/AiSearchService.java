package com.practices.ai.service;

import org.springframework.stereotype.Service;

import com.practices.ai.controller.dto.SearchResponse;
import com.practices.ai.tools.EmployeeSearchTool;

@Service
public class AiSearchService {
    private final EmployeeSearchTool searchTool;
    public AiSearchService(EmployeeSearchTool searchTool) { this.searchTool = searchTool; }

    public SearchResponse search(String query) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Search query is required.");
        String normalized = query.trim();
        if (normalized.length() > 200) throw new IllegalArgumentException("Search query is too long.");
        return new SearchResponse(normalized, searchTool.search(normalized));
    }
}
