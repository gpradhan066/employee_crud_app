package com.practices.ai.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String defaultModel,
        int maxHistoryMessages,
        int summaryTriggerMessages,
        Provider provider) {

    public AiProperties {
        defaultModel = defaultModel == null || defaultModel.isBlank() ? "local-assistant" : defaultModel;
        maxHistoryMessages = maxHistoryMessages <= 0 ? 40 : maxHistoryMessages;
        summaryTriggerMessages = summaryTriggerMessages <= 0 ? maxHistoryMessages * 2 : summaryTriggerMessages;
        provider = provider == null ? new Provider(null, null, null, null, null) : provider;
    }

    public record Provider(String baseUrl, String apiKey, List<String> models, Duration timeout, String systemPrompt) {
        public Provider {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl;
            models = models == null ? List.of() : List.copyOf(models);
            timeout = timeout == null ? Duration.ofSeconds(45) : timeout;
            systemPrompt = systemPrompt == null || systemPrompt.isBlank()
                    ? "You are a concise employee-management assistant. Protect private data and never invent records."
                    : systemPrompt;
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank() && !models.isEmpty();
        }
    }
}
