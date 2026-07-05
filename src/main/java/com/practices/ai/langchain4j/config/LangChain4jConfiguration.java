package com.practices.ai.langchain4j.config;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.practices.ai.config.AiConfiguration.ProviderConfig;
import com.practices.ai.langchain4j.agent.EmployeeAIAgent;
import com.practices.ai.langchain4j.tools.EmployeeTools;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

@Configuration
public class LangChain4jConfiguration {
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "qwen3:1.7b";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    @Bean
    ChatModel langchain4jChatModel(Map<String, ProviderConfig> aiProviders) {
        ProviderConfig ollama = aiProviders.get("ollama");
        String baseUrl = ollama != null && ollama.baseUrl() != null && !ollama.baseUrl().isBlank()
                ? ollama.baseUrl() : DEFAULT_BASE_URL;
        String modelName = ollama != null && !ollama.models().isEmpty() ? ollama.models().get(0) : DEFAULT_MODEL;
        Duration timeout = ollama != null && ollama.timeout() != null ? ollama.timeout() : DEFAULT_TIMEOUT;

        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(timeout)
                .build();
    }

    @Bean
    EmbeddingModel langchain4jEmbeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    EmbeddingStore<TextSegment> employeeEmbeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    ContentRetriever employeeContentRetriever(
            EmbeddingStore<TextSegment> employeeEmbeddingStore, EmbeddingModel langchain4jEmbeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(employeeEmbeddingStore)
                .embeddingModel(langchain4jEmbeddingModel)
                .maxResults(5)
                .minScore(0.0)
                .build();
    }

    @Bean
    ChatMemoryProvider employeeChatMemoryProvider() {
        Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();
        return memoryId -> memories.computeIfAbsent(memoryId,
                id -> MessageWindowChatMemory.builder().id(id).maxMessages(20).build());
    }

    @Bean
    EmployeeAIAgent employeeAIAgent(
            ChatModel langchain4jChatModel,
            ChatMemoryProvider employeeChatMemoryProvider,
            ContentRetriever employeeContentRetriever,
            EmployeeTools employeeTools) {
        return AiServices.builder(EmployeeAIAgent.class)
                .chatModel(langchain4jChatModel)
                .chatMemoryProvider(employeeChatMemoryProvider)
                .contentRetriever(employeeContentRetriever)
                .tools(employeeTools)
                .build();
    }
}
