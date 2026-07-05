package com.practices.ai.embedding.dto;

import java.time.Instant;

public record DocumentResponse(Long id, String title, String content, Long employeeId, Instant createdAt) {
}
