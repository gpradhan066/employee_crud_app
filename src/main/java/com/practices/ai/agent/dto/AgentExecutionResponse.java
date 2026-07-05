package com.practices.ai.agent.dto;

import java.time.Instant;
import java.util.List;

public record AgentExecutionResponse(
        String conversationId,
        String planId,
        boolean executed,
        String summary,
        List<ProposedChange> appliedChanges,
        Instant createdAt) {
}
