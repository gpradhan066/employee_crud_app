package com.practices.ai.mcp.client.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.practices.ai.controller.dto.ChatRequest;
import com.practices.ai.controller.dto.ChatResponse;
import com.practices.ai.mcp.client.McpChatService;

@CrossOrigin
@RestController
@RequestMapping("/api/ai/mcp")
public class McpChatController {
    private final McpChatService mcpChatService;

    public McpChatController(McpChatService mcpChatService) {
        this.mcpChatService = mcpChatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("Message is required.");
        }
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString() : request.conversationId().trim();
        boolean think = request.think() == null || request.think();
        return ResponseEntity.ok(mcpChatService.chat(conversationId, request.message().trim(), request.model(), think));
    }
}
