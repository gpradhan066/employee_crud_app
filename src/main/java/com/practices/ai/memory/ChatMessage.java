package com.practices.ai.memory;

import java.time.Instant;

public record ChatMessage(Role role, String content, Instant createdAt) {
    public enum Role { USER, ASSISTANT }
}
