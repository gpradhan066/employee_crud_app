package com.practices.ai.provider;

import java.util.List;

import org.springframework.stereotype.Component;

import com.practices.ai.controller.dto.ModelResponse;
import com.practices.ai.memory.ChatMessage;

@Component
public class LocalAiProvider implements AiProvider {
    public static final String MODEL = "local-assistant";

    @Override public boolean supports(String model) { return MODEL.equals(model); }

    @Override
    public String complete(String model, String systemPrompt, List<ChatMessage> history, String message) {
        return "AI connectivity is ready. Configure AI_API_KEY and AI_MODELS to use a hosted model. "
                + "You asked: \"" + message + "\"";
    }

    @Override
    public List<ModelResponse> models() {
        return List.of(new ModelResponse(MODEL, "Local fallback", "Built-in", true));
    }
}
