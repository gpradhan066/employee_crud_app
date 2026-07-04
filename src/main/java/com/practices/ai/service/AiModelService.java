package com.practices.ai.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.practices.ai.controller.dto.ModelResponse;
import com.practices.ai.provider.AiProviderRegistry;
import com.practices.ai.provider.LocalAiProvider;

@Service
public class AiModelService {
    private final AiProviderRegistry providers;

    public AiModelService(AiProviderRegistry providers) {
        this.providers = providers;
    }

    public List<ModelResponse> models() {
        List<ModelResponse> configured = providers.models();
        boolean externalModelAvailable = configured.stream()
                .anyMatch(model -> model.available() && !LocalAiProvider.MODEL.equals(model.id()));

        List<ModelResponse> visibleModels = configured.stream()
                .filter(model -> !externalModelAvailable || !LocalAiProvider.MODEL.equals(model.id()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        visibleModels.add(new ModelResponse("gpt-4.1-mini", "ChatGPT (not configured)", "OpenAI", false));
        visibleModels.add(new ModelResponse("gemini", "Gemini (not configured)", "Google", false));
        visibleModels.add(new ModelResponse("claude", "Claude (not configured)", "Anthropic", false));
        visibleModels.add(new ModelResponse("bedrock", "Bedrock (not configured)", "AWS", false));
        return List.copyOf(visibleModels);
    }
}