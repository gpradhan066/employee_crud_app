package com.practices.ai.config;

import java.net.http.HttpClient;

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
}
