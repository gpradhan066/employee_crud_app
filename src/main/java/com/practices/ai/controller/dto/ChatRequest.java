package com.practices.ai.controller.dto;

public record ChatRequest(String conversationId, String message, String model, Boolean think) {
}
