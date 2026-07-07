package com.practices.ai.memory.dto;

public record UserPreferenceRequest(
        String userId, String preferredProvider, String preferredModel, Boolean thinkingEnabled) {
}
