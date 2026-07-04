package com.practices.ai.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.practices.ai.controller.dto.ChatRequest;
import com.practices.ai.controller.dto.ChatResponse;
import com.practices.ai.controller.dto.ModelResponse;
import com.practices.ai.controller.dto.SearchResponse;
import com.practices.ai.service.AiChatService;
import com.practices.ai.service.AiModelService;
import com.practices.ai.service.AiSearchService;

@CrossOrigin
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiChatService chatService;
    private final AiModelService modelService;
    private final AiSearchService searchService;

    public AiController(AiChatService chatService, AiModelService modelService, AiSearchService searchService) {
        this.chatService = chatService;
        this.modelService = modelService;
        this.searchService = searchService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.chat(request));
    }

    @GetMapping("/models")
    public List<ModelResponse> models() {
        return modelService.models();
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam String query) {
        return searchService.search(query);
    }
}
