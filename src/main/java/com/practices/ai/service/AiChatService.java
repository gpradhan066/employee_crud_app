package com.practices.ai.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.practices.ai.agent.AiAgent;
import com.practices.ai.config.AiProperties;
import com.practices.ai.controller.dto.ChatRequest;
import com.practices.ai.controller.dto.ChatResponse;
import com.practices.ai.memory.ChatMessage;
import com.practices.ai.memory.ChatMessage.Role;
import com.practices.ai.memory.ConversationMemory;

@Service
public class AiChatService {
    private static final int MAX_MESSAGE_LENGTH = 8_000;
    private final AiAgent agent;
    private final ConversationMemory memory;
    private final AiProperties properties;

    public AiChatService(AiAgent agent, ConversationMemory memory, AiProperties properties) {
        this.agent = agent;
        this.memory = memory;
        this.properties = properties;
    }

    public ChatResponse chat(ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank())
            throw new IllegalArgumentException("Message is required.");
        String message = request.message().trim();
        if (message.length() > MAX_MESSAGE_LENGTH)
            throw new IllegalArgumentException("Message must not exceed " + MAX_MESSAGE_LENGTH + " characters.");

        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString() : request.conversationId().trim();
        String model = request.model() == null || request.model().isBlank()
                ? properties.defaultModel() : request.model().trim();
        String answer = agent.respond(model, memory.get(conversationId), message);
        Instant now = Instant.now();
        memory.append(conversationId, new ChatMessage(Role.USER, message, now));
        memory.append(conversationId, new ChatMessage(Role.ASSISTANT, answer, now));
        return new ChatResponse(conversationId, answer, model, now);
    }
}
