package com.practices.ai.config;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {
    private static final Map<String, String> PROVIDER_BEANS = Map.of(
            "ollama", "ollamaChatModel",
            "openai", "openAiChatModel",
            "anthropic", "anthropicChatModel",
            "bedrock-converse", "bedrockProxyChatModel");

    @Bean
    ChatClient chatClient(
            Map<String, ChatModel> chatModels,
            @Value("${spring.ai.model.chat:ollama}") String provider) {
        String normalizedProvider = provider.trim().toLowerCase();
        String beanName = PROVIDER_BEANS.get(normalizedProvider);
        if (beanName == null) {
            throw new IllegalStateException("Unsupported AI provider: " + provider
                    + ". Supported providers: " + String.join(", ", PROVIDER_BEANS.keySet()));
        }
        ChatModel selectedModel = chatModels.get(beanName);
        if (selectedModel == null) {
            throw new IllegalStateException("AI provider '" + normalizedProvider
                    + "' is selected but its ChatModel bean is unavailable.");
        }
        return ChatClient.builder(selectedModel).build();
    }
}