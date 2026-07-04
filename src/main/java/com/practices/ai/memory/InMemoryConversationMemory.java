package com.practices.ai.memory;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.practices.ai.config.AiProperties;

@Component
public class InMemoryConversationMemory implements ConversationMemory {
    private final Map<String, ArrayDeque<ChatMessage>> conversations = new ConcurrentHashMap<>();
    private final int limit;

    public InMemoryConversationMemory(AiProperties properties) {
        limit = properties.maxHistoryMessages();
    }

    @Override
    public List<ChatMessage> get(String conversationId) {
        ArrayDeque<ChatMessage> messages = conversations.get(conversationId);
        if (messages == null) return List.of();
        synchronized (messages) { return List.copyOf(messages); }
    }

    @Override
    public void append(String conversationId, ChatMessage message) {
        ArrayDeque<ChatMessage> messages = conversations.computeIfAbsent(conversationId, ignored -> new ArrayDeque<>());
        synchronized (messages) {
            messages.addLast(message);
            while (messages.size() > limit) messages.removeFirst();
        }
    }

    @Override
    public void clear(String conversationId) {
        conversations.remove(conversationId);
    }
}
