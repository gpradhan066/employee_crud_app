package com.practices.ai.memory.dto;

import java.time.Instant;

public record SessionMessageResponse(String role, String content, Instant createdAt) {
}
