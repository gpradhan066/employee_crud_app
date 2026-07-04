package com.practices.ai.agent;

import java.util.List;

import org.springframework.stereotype.Component;

import com.practices.ai.memory.ChatMessage;
import com.practices.ai.prompt.PromptBuilder;
import com.practices.ai.provider.AiProvider;
import com.practices.ai.provider.AiProviderRegistry;
import com.practices.ai.rag.ContextRetriever;

@Component
public class AiAgent {
    private final AiProviderRegistry providers;
    private final PromptBuilder prompts;
    private final ContextRetriever retriever;

    public AiAgent(AiProviderRegistry providers, PromptBuilder prompts, ContextRetriever retriever) {
        this.providers = providers;
        this.prompts = prompts;
        this.retriever = retriever;
    }

    public String respond(String model, List<ChatMessage> history, String message) {
        AiProvider provider = providers.providerFor(model);
        return provider.complete(model, prompts.systemPrompt(retriever.retrieve(message)), prompts.history(history), message);
    }
}
