package com.practices.ai.memory.dto;

import java.time.Instant;

public record UserPreferenceResponse(
        String userId, String preferredProvider, String preferredModel, Boolean thinkingEnabled, Instant updatedAt) {
}
