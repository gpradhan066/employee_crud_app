package com.practices.ai.prompt;

import java.util.List;

import org.springframework.stereotype.Component;

import com.practices.ai.config.AiProperties;
import com.practices.ai.memory.ChatMessage;

@Component
public class PromptBuilder {
    private final AiProperties properties;

    public PromptBuilder(AiProperties properties) { this.properties = properties; }

    public String systemPrompt(List<String> context) {
        if (context.isEmpty()) return properties.provider().systemPrompt();
        return properties.provider().systemPrompt() + "\nUse this application context when relevant:\n- " + String.join("\n- ", context);
    }

    public List<ChatMessage> history(List<ChatMessage> history) { return List.copyOf(history); }
}
