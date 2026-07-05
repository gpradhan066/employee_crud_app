package com.practices.ai.langchain4j.dto;

import java.time.Instant;

public record EmployeeAnalysisResponse(String conversationId, EmployeeAnswer answer, String model, Instant createdAt) {
}
