package com.practices.ai.rag.dto;

import java.time.Instant;

public record PolicyDocumentResponse(Long id, String filename, String category, Integer chunkCount, Instant uploadedAt) {
}
