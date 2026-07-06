package com.practices.ai.rag.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.practices.ai.controller.dto.ChatRequest;
import com.practices.ai.controller.dto.ChatResponse;
import com.practices.ai.rag.PolicyRagService;

@CrossOrigin
@RestController
@RequestMapping("/api/ai/rag")
public class PolicyRagController {
    private final PolicyRagService ragService;

    public PolicyRagController(PolicyRagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("Message is required.");
        }
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString() : request.conversationId().trim();
        boolean think = request.think() == null || request.think();
        return ResponseEntity.ok(ragService.chat(conversationId, request.message().trim(), think));
    }
}
