package com.practices.ai.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.practices.ai.config.AiProperties;
import com.practices.ai.controller.dto.ChatRequest;
import com.practices.ai.controller.dto.ChatResponse;
import com.practices.ai.memory.ChatMessage;
import com.practices.ai.memory.ChatMessage.Role;
import com.practices.ai.memory.ConversationMemory;

import reactor.core.publisher.Flux;

@Service
public class AiChatService {
    private static final int MAX_MESSAGE_LENGTH = 8_000;
    private final AIService aiService;
    private final ConversationMemory memory;
    private final AiProperties properties;

    public AiChatService(AIService aiService, ConversationMemory memory, AiProperties properties) {
        this.aiService = aiService;
        this.memory = memory;
        this.properties = properties;
    }

    public ChatResponse chat(ChatRequest request) {
        ValidatedChat validated = validate(request);
        String answer = aiService.chat(
                validated.message(),
                validated.model(),
                memory.get(validated.conversationId()));
        Instant now = Instant.now();
        storeConversation(validated, answer, now);
        return new ChatResponse(validated.conversationId(), answer, validated.model(), now);
    }

    public Flux<String> stream(ChatRequest request) {
        ValidatedChat validated = validate(request);
        StringBuilder answer = new StringBuilder();
        return aiService.stream(
                        validated.message(),
                        validated.model(),
                        memory.get(validated.conversationId()))
                .doOnNext(answer::append)
                .doOnComplete(() -> storeConversation(validated, answer.toString(), Instant.now()));
    }

    private ValidatedChat validate(ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank())
            throw new IllegalArgumentException("Message is required.");
        String message = request.message().trim();
        if (message.length() > MAX_MESSAGE_LENGTH)
            throw new IllegalArgumentException("Message must not exceed " + MAX_MESSAGE_LENGTH + " characters.");
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString() : request.conversationId().trim();
        String model = request.model() == null || request.model().isBlank()
                ? properties.defaultModel() : request.model().trim();
        return new ValidatedChat(conversationId, message, model);
    }

    private void storeConversation(ValidatedChat chat, String answer, Instant createdAt) {
        memory.append(chat.conversationId(), new ChatMessage(Role.USER, chat.message(), createdAt));
        memory.append(chat.conversationId(), new ChatMessage(Role.ASSISTANT, answer, createdAt));
    }

    private record ValidatedChat(String conversationId, String message, String model) {
    }
}