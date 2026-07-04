package com.practices.ai.provider;

import java.util.List;

import org.springframework.stereotype.Component;

import com.practices.ai.controller.dto.ModelResponse;

@Component
public class AiProviderRegistry {
    private final List<AiProvider> providers;

    public AiProviderRegistry(List<AiProvider> providers) { this.providers = List.copyOf(providers); }

    public AiProvider providerFor(String model) {
        return providers.stream().filter(provider -> provider.supports(model)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown or unavailable AI model: " + model));
    }

    public List<ModelResponse> models() { return providers.stream().flatMap(p -> p.models().stream()).toList(); }
}
