package com.practices.ai.agent.dto;

import java.time.Instant;
import java.util.List;

public record AgentPlanResponse(
        String conversationId,
        String planId,
        String reasoning,
        String clarificationNeeded,
        List<ProposedChange> proposedChanges,
        boolean actionable,
        Instant createdAt) {
}
