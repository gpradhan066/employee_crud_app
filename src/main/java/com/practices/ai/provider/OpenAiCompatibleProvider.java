package com.practices.ai.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.practices.ai.config.AiProperties;
import com.practices.ai.controller.dto.ModelResponse;
import com.practices.ai.memory.ChatMessage;

@Component
public class OpenAiCompatibleProvider implements AiProvider {
    private final AiProperties properties;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public OpenAiCompatibleProvider(AiProperties properties, HttpClient client, ObjectMapper mapper) {
        this.properties = properties;
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String model) {
        return properties.provider().isConfigured() && properties.provider().models().contains(model);
    }

    @Override
    public String complete(String model, String systemPrompt, List<ChatMessage> history, String message) {
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            history.forEach(item -> messages.add(Map.of(
                    "role", item.role() == ChatMessage.Role.USER ? "user" : "assistant",
                    "content", item.content())));
            messages.add(Map.of("role", "user", "content", message));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("messages", messages);
            payload.put("temperature", 0.2);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.provider().baseUrl().replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(properties.provider().timeout())
                    .header("Authorization", "Bearer " + properties.provider().apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI provider returned status " + response.statusCode());
            }
            JsonNode content = mapper.readTree(response.body()).at("/choices/0/message/content");
            if (!content.isTextual()) throw new IllegalStateException("AI provider returned an invalid response");
            return content.asText();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI request was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("AI provider request failed", exception);
        }
    }

    @Override
    public List<ModelResponse> models() {
        boolean ollama = properties.provider().baseUrl().contains("localhost:11434")
                || properties.provider().baseUrl().contains("127.0.0.1:11434");
        String providerName = ollama ? "Ollama" : "OpenAI compatible";
        return properties.provider().models().stream()
                .map(model -> new ModelResponse(
                        model,
                        ollama ? "Ollama: " + model : model,
                        providerName,
                        properties.provider().isConfigured()))
                .toList();
    }
}
