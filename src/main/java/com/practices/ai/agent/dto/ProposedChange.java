package com.practices.ai.agent.dto;

public record ProposedChange(Long employeeId, String name, String field, String beforeValue, String afterValue) {
}
