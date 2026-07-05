package com.practices.ai.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.practices.ai.memory.ChatMessage;
import com.practices.ai.tools.EmployeeCrudTools;

import reactor.core.publisher.Flux;

@Service
public class AIService {
    private final ChatClient chatClient;
    private final Resource systemPrompt;
    private final Resource userPrompt;
    private final EmployeeCrudTools employeeCrudTools;

    public AIService(
            ChatClient chatClient,
            @Value("classpath:/prompts/employee-assistant-system.st") Resource systemPrompt,
            @Value("classpath:/prompts/employee-assistant-user.st") Resource userPrompt,
            EmployeeCrudTools employeeCrudTools) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.employeeCrudTools = employeeCrudTools;
    }

    public String chat(String message, String model, List<ChatMessage> history, List<String> context, boolean think) {
        return stripAssistantPrefix(request(message, model, history, context, think).call().content());
    }

    public Flux<String> stream(
            String message, String model, List<ChatMessage> history, List<String> context, boolean think) {
        return request(message, model, history, context, think).stream().content();
    }

    private ChatClient.ChatClientRequestSpec request(
            String message, String model, List<ChatMessage> history, List<String> context, boolean think) {
        String renderedSystemPrompt = new PromptTemplate(systemPrompt).render(Map.of(
                "history", formatHistory(history),
                "context", formatContext(context)));
        String renderedUserPrompt = new PromptTemplate(userPrompt).render(Map.of(
                "message", message));

        OllamaChatOptions.Builder options = OllamaChatOptions.builder().model(model);
        if (think) {
            options.enableThinking();
        } else {
            options.disableThinking();
        }

        return chatClient.prompt()
                .system(renderedSystemPrompt)
                .user(renderedUserPrompt)
                .tools(employeeCrudTools)
                .options(options);
    }

    private String stripAssistantPrefix(String answer) {
        if (answer == null) {
            return answer;
        }
        String trimmed = answer.strip();
        if (trimmed.regionMatches(true, 0, "assistant:", 0, "assistant:".length())) {
            return trimmed.substring("assistant:".length()).strip();
        }
        return trimmed;
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

    private String formatContext(List<String> context) {
        if (context == null || context.isEmpty()) {
            return "No additional context.";
        }
        return context.stream()
                .filter(item -> item != null && !item.isBlank())
                .reduce((left, right) -> left + "\n- " + right)
                .map(items -> "- " + items)
                .orElse("No additional context.");
    }
}
