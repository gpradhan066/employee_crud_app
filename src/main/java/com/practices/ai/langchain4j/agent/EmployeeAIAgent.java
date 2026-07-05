package com.practices.ai.langchain4j.agent;

import com.practices.ai.langchain4j.dto.EmployeeAnswer;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface EmployeeAIAgent {

    @SystemMessage(fromResource = "langchain4j/employee-agent-system.txt")
    String chat(@MemoryId String conversationId, @UserMessage String message);

    @SystemMessage(fromResource = "langchain4j/employee-agent-system.txt")
    EmployeeAnswer analyze(@MemoryId String conversationId, @UserMessage String message);
}
