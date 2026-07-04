package com.practices.ai.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.practices.ai.memory.ChatMessage;

import reactor.core.publisher.Flux;

@Service
public class AIService {
    private final ChatClient chatClient;
    private final Resource systemPrompt;
    private final Resource userPrompt;

    public AIService(
            ChatClient chatClient,
            @Value("classpath:/prompts/employee-assistant-system.st") Resource systemPrompt,
            @Value("classpath:/prompts/employee-assistant-user.st") Resource userPrompt) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
    }

    public String chat(String message, String model, List<ChatMessage> history) {
        return request(message, model, history).call().content();
    }

    public Flux<String> stream(String message, String model, List<ChatMessage> history) {
        return request(message, model, history).stream().content();
    }

    private ChatClient.ChatClientRequestSpec request(
            String message, String model, List<ChatMessage> history) {
        String renderedSystemPrompt = new PromptTemplate(systemPrompt).render(Map.of(
                "history", formatHistory(history)));
        String renderedUserPrompt = new PromptTemplate(userPrompt).render(Map.of(
                "message", message));

        return chatClient.prompt()
                .system(renderedSystemPrompt)
                .user(renderedUserPrompt)
                .options(ChatOptions.builder().model(model));
    }

    private String formatHistory(List<ChatMessage> history) {
        if (history.isEmpty()) {
            return "No previous conversation.";
        }
        return history.stream()
                .map(item -> item.role().name() + ": " + item.content())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No previous conversation.");
    }
}
