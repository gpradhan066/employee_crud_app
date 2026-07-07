package com.practices.ai.memory.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.practices.ai.memory.ConversationSessionQueryService;
import com.practices.ai.memory.dto.RecentQuestionResponse;
import com.practices.ai.memory.dto.SessionMessageResponse;
import com.practices.ai.memory.dto.SessionSummaryResponse;

@CrossOrigin
@RestController
@RequestMapping("/api/ai")
public class ConversationSessionController {
    private final ConversationSessionQueryService sessionService;

    public ConversationSessionController(ConversationSessionQueryService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/sessions")
    public List<SessionSummaryResponse> listSessions(@RequestParam(defaultValue = "50") int limit) {
        return sessionService.listSessions(limit);
    }

    @GetMapping("/sessions/{conversationId}/messages")
    public List<SessionMessageResponse> getMessages(@PathVariable String conversationId) {
        return sessionService.getMessages(conversationId);
    }

    @DeleteMapping("/sessions/{conversationId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String conversationId) {
        sessionService.deleteSession(conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/recent-questions")
    public List<RecentQuestionResponse> recentQuestions(@RequestParam(defaultValue = "10") int limit) {
        return sessionService.recentQuestions(limit);
    }
}
