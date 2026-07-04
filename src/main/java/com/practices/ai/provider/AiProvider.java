package com.practices.ai.provider;

import java.util.List;

import com.practices.ai.controller.dto.ModelResponse;
import com.practices.ai.memory.ChatMessage;

public interface AiProvider {
    boolean supports(String model);
    String complete(String model, String systemPrompt, List<ChatMessage> history, String message);
    List<ModelResponse> models();
}
