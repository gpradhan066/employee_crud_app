package com.practices.ai.langchain4j.dto;

import java.util.List;

public record EmployeeAnswer(String answer, List<String> mentionedEmployees) {
}
