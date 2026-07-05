package com.practices.ai.agent.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.practices.ai.agent.EmployeeAgentService;
import com.practices.ai.agent.dto.AgentConfirmRequest;
import com.practices.ai.agent.dto.AgentExecutionResponse;
import com.practices.ai.agent.dto.AgentPlanRequest;
import com.practices.ai.agent.dto.AgentPlanResponse;

@CrossOrigin
@RestController
@RequestMapping("/api/ai/agent")
public class EmployeeAgentController {
    private final EmployeeAgentService agent;

    public EmployeeAgentController(EmployeeAgentService agent) {
        this.agent = agent;
    }

    @PostMapping("/plan")
    public ResponseEntity<AgentPlanResponse> plan(@RequestBody AgentPlanRequest request) {
        if (request == null || request.instruction() == null || request.instruction().isBlank()) {
            throw new IllegalArgumentException("Instruction is required.");
        }
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString() : request.conversationId().trim();
        return ResponseEntity.ok(agent.plan(conversationId, request.instruction().trim()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<AgentExecutionResponse> confirm(@RequestBody AgentConfirmRequest request) {
        if (request == null || request.planId() == null || request.planId().isBlank()) {
            throw new IllegalArgumentException("planId is required.");
        }
        if (request.conversationId() == null || request.conversationId().isBlank()) {
            throw new IllegalArgumentException("conversationId is required.");
        }
        return ResponseEntity.ok(agent.confirm(request.conversationId().trim(), request.planId().trim(), request.confirm()));
    }
}
