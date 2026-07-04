package com.practices.ai.memory;

import java.util.List;

public interface ConversationMemory {
    List<ChatMessage> get(String conversationId);
    void append(String conversationId, ChatMessage message);
    void clear(String conversationId);
}
