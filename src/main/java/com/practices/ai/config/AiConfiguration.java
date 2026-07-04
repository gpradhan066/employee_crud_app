package com.practices.ai.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {
    @Bean
    HttpClient aiHttpClient(AiProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.provider().timeout())
                .build();
    }

    @Bean
    @ConfigurationProperties(prefix = "app.ai.providers")
    Map<String, ProviderConfig> aiProviders() {
        return new LinkedHashMap<>();
    }

    public record ProviderConfig(String name, String baseUrl, String apiKey, List<String> models, Duration timeout) {
        public ProviderConfig {
            models = models == null ? List.of() : List.copyOf(models);
        }

        public boolean isConfigured() {
            return !models.isEmpty();
        }
    }
}
