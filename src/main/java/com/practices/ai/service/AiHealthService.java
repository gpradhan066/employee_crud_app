package com.practices.ai.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.practices.ai.config.AiConfiguration.ProviderConfig;
import com.practices.ai.config.AiProperties;

@Service
public class AiHealthService {
    private final AiProperties properties;
    private final Map<String, ProviderConfig> providersConfig;
    private final HttpClient httpClient;

    public AiHealthService(AiProperties properties, Map<String, ProviderConfig> providersConfig) {
        this.properties = properties;
        this.providersConfig = providersConfig;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public Map<String, Object> status() {
        ProviderConfig ollama = providersConfig.get("ollama");
        String configuredBaseUrl = ollama != null ? ollama.baseUrl() : properties.provider().baseUrl();
        String apiKey = ollama != null ? ollama.apiKey() : properties.provider().apiKey();
        Duration timeout = ollama != null && ollama.timeout() != null ? ollama.timeout() : properties.provider().timeout();
        String baseUrl = normalizeBaseUrl(configuredBaseUrl);
        if (baseUrl == null || baseUrl.isBlank()) {
            return Map.of(
                    "status", "DOWN",
                    "provider", "Ollama",
                    "message", "Ollama base URL is not configured.");
        }

        try {
            String modelsEndpoint = baseUrl.endsWith("/v1") ? baseUrl + "/models" : baseUrl + "/v1/models";
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(modelsEndpoint))
                    .timeout(timeout)
                    .GET();
            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            boolean healthy = response.statusCode() >= 200 && response.statusCode() < 300;
            return Map.of(
                    "status", healthy ? "UP" : "DOWN",
                    "provider", "Ollama",
                    "baseUrl", baseUrl,
                    "code", response.statusCode(),
                    "message", healthy ? "OK" : "Unexpected status from Ollama");
        } catch (Exception exception) {
            return Map.of(
                    "status", "DOWN",
                    "provider", "Ollama",
                    "baseUrl", baseUrl,
                    "message", exception.getMessage());
        }
    }

    private String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.replaceAll("/+$", "");
    }
}
