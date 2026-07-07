package com.practices.ai.memory.dto;

import java.time.Instant;

public record SessionSummaryResponse(
        String conversationId, String title, long messageCount, Instant startedAt, Instant lastActivityAt) {
}
