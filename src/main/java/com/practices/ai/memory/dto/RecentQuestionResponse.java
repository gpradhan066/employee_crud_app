package com.practices.ai.memory.dto;

import java.time.Instant;

public record RecentQuestionResponse(String conversationId, String content, Instant createdAt) {
}
