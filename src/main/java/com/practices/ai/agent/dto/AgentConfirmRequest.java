package com.practices.ai.agent.dto;

public record AgentConfirmRequest(String conversationId, String planId, boolean confirm) {
}
