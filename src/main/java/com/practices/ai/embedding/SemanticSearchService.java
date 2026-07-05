package com.practices.ai.embedding;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.practices.ai.embedding.dto.SemanticSearchResponse;
import com.practices.ai.embedding.dto.SemanticSearchResult;

@Service
public class SemanticSearchService {
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_QUERY_LENGTH = 200;

    private final VectorStore vectorStore;

    public SemanticSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public SemanticSearchResponse search(String query, Integer topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query is required.");
        }
        String normalized = query.trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Search query is too long.");
        }

        List<Document> matches = vectorStore.similaritySearch(SearchRequest.builder()
                .query(normalized)
                .topK(topK != null && topK > 0 ? topK : DEFAULT_TOP_K)
                .build());

        List<SemanticSearchResult> results = matches.stream().map(this::toResult).toList();
        return new SemanticSearchResponse(normalized, results);
    }

    private SemanticSearchResult toResult(Document document) {
        Object type = document.getMetadata().get("type");
        Object referenceId = document.getMetadata().get("referenceId");
        String title = switch (String.valueOf(type)) {
            case "employee" -> String.valueOf(document.getMetadata().get("name"));
            case "document" -> String.valueOf(document.getMetadata().get("title"));
            default -> "Unknown";
        };
        Long refId = referenceId instanceof Number number ? number.longValue() : null;

        return new SemanticSearchResult(String.valueOf(type), refId, title, document.getText(), document.getScore());
    }
}
