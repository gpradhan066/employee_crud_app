package com.practices.ai.embedding.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.practices.ai.embedding.EmbeddingIngestionService;
import com.practices.ai.embedding.SemanticSearchService;
import com.practices.ai.embedding.dto.ReindexResponse;
import com.practices.ai.embedding.dto.SemanticSearchResponse;

@CrossOrigin
@RestController
@RequestMapping("/api/ai")
public class SemanticSearchController {
    private final SemanticSearchService searchService;
    private final EmbeddingIngestionService ingestionService;

    public SemanticSearchController(SemanticSearchService searchService, EmbeddingIngestionService ingestionService) {
        this.searchService = searchService;
        this.ingestionService = ingestionService;
    }

    @GetMapping("/semantic-search")
    public ResponseEntity<SemanticSearchResponse> search(
            @RequestParam String query, @RequestParam(required = false) Integer topK) {
        return ResponseEntity.ok(searchService.search(query, topK));
    }

    @PostMapping("/semantic-search/reindex")
    public ResponseEntity<ReindexResponse> reindex() {
        return ResponseEntity.ok(ingestionService.reindexAll());
    }
}
