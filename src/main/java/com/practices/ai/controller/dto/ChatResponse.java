package com.practices.ai.controller.dto;

import java.time.Instant;

public record ChatResponse(String conversationId, String message, String model, Instant createdAt) {
}
