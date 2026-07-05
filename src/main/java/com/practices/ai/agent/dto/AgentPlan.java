package com.practices.ai.agent.dto;

public record AgentPlan(String reasoning, Filter filter, Action action, String clarificationNeeded) {

    public record Filter(
            String department,
            String role,
            String performance,
            Double minSalary,
            Double maxSalary,
            String nameContains) {
    }

    public record Action(String type, Double amount, String textValue) {
    }
}
