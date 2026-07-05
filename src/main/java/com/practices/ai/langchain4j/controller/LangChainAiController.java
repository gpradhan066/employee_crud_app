package com.practices.ai.langchain4j.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.practices.ai.controller.dto.ChatRequest;
import com.practices.ai.controller.dto.ChatResponse;
import com.practices.ai.langchain4j.agent.EmployeeAIAgent;
import com.practices.ai.langchain4j.dto.EmployeeAnalysisResponse;

@CrossOrigin
@RestController
@RequestMapping("/api/ai/langchain")
public class LangChainAiController {
    private static final String MODEL_LABEL = "langchain4j:ollama";

    private final EmployeeAIAgent employeeAIAgent;

    public LangChainAiController(EmployeeAIAgent employeeAIAgent) {
        this.employeeAIAgent = employeeAIAgent;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String conversationId = resolveConversationId(request);
        String reply = employeeAIAgent.chat(conversationId, resolveMessage(request));
        return ResponseEntity.ok(new ChatResponse(conversationId, reply, MODEL_LABEL, Instant.now()));
    }

    @PostMapping("/analyze")
    public ResponseEntity<EmployeeAnalysisResponse> analyze(@RequestBody ChatRequest request) {
        String conversationId = resolveConversationId(request);
        var answer = employeeAIAgent.analyze(conversationId, resolveMessage(request));
        return ResponseEntity.ok(new EmployeeAnalysisResponse(conversationId, answer, MODEL_LABEL, Instant.now()));
    }

    private String resolveMessage(ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("Message is required.");
        }
        return request.message().trim();
    }

    private String resolveConversationId(ChatRequest request) {
        return request != null && request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId().trim()
                : UUID.randomUUID().toString();
    }
}
