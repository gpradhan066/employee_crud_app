package com.practices.ai.mcp.client;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.practices.ai.config.AiProperties;
import com.practices.ai.controller.dto.ChatResponse;
import com.practices.ai.memory.ChatMessage;
import com.practices.ai.memory.ChatMessage.Role;
import com.practices.ai.memory.ConversationMemory;

/**
 * Chat backed by this application's own MCP server ({@code com.practices.ai.mcp}), reached as an
 * MCP client rather than through Spring AI's in-process {@code @Tool} wiring
 * ({@code com.practices.ai.tools.EmployeeCrudTools} / {@code AIService}).
 *
 * <p>This app is unusually both the MCP server and, here, its own MCP client - so the connection
 * to {@code /mcp} is opened lazily on first use, never during bean creation/application startup,
 * since Tomcat is not guaranteed to be listening yet at that point.</p>
 */
@Service
public class McpChatService {
    private static final Logger log = LoggerFactory.getLogger(McpChatService.class);
    private static final String MODEL_LABEL_SUFFIX = " (via MCP)";

    private final ChatClient chatClient;
    private final Resource systemPrompt;
    private final Resource userPrompt;
    private final ConversationMemory memory;
    private final AiProperties aiProperties;
    private final String serverBaseUrl;

    private volatile McpSyncClient mcpSyncClient;
    private final Object clientLock = new Object();

    public McpChatService(
            ChatClient chatClient,
            @Value("classpath:/prompts/mcp-assistant-system.st") Resource systemPrompt,
            @Value("classpath:/prompts/employee-assistant-user.st") Resource userPrompt,
            ConversationMemory memory,
            AiProperties aiProperties,
            @Value("${server.port:8080}") int serverPort) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.memory = memory;
        this.aiProperties = aiProperties;
        this.serverBaseUrl = "http://localhost:" + serverPort;
    }

    public ChatResponse chat(String conversationId, String message, String model, boolean think) {
        Instant now = Instant.now();
        memory.append(conversationId, new ChatMessage(Role.USER, message, now));

        String effectiveModel = (model == null || model.isBlank()) ? aiProperties.defaultModel() : model;

        ToolCallbackProvider toolCallbackProvider;
        try {
            toolCallbackProvider = new SyncMcpToolCallbackProvider(mcpClient());
        } catch (Exception exception) {
            log.warn("[MCP-CHAT] conversationId={} could not reach this app's own MCP server", conversationId, exception);
            String answer = "The MCP tool connection isn't available right now - please try again in a moment.";
            memory.append(conversationId, new ChatMessage(Role.ASSISTANT, answer, now));
            return new ChatResponse(conversationId, answer, effectiveModel + MODEL_LABEL_SUFFIX, now);
        }

        String renderedSystem = new PromptTemplate(systemPrompt).render(Map.of(
                "history", formatHistory(memory.get(conversationId))));
        String renderedUser = new PromptTemplate(userPrompt).render(Map.of("message", message));

        OllamaChatOptions.Builder options = OllamaChatOptions.builder().model(effectiveModel);
        if (think) {
            options.enableThinking();
        } else {
            options.disableThinking();
        }

        String answer = stripAssistantPrefix(chatClient.prompt()
                .system(renderedSystem)
                .user(renderedUser)
                .toolCallbacks(toolCallbackProvider)
                .options(options)
                .call()
                .content());

        Instant answeredAt = Instant.now();
        memory.append(conversationId, new ChatMessage(Role.ASSISTANT, answer, answeredAt));

        return new ChatResponse(conversationId, answer, effectiveModel + MODEL_LABEL_SUFFIX, answeredAt);
    }

    private McpSyncClient mcpClient() {
        McpSyncClient client = mcpSyncClient;
        if (client != null) {
            return client;
        }
        synchronized (clientLock) {
            if (mcpSyncClient == null) {
                McpClientTransport transport = HttpClientStreamableHttpTransport.builder(serverBaseUrl).build();
                McpSyncClient built = McpClient.sync(transport)
                        .clientInfo(new McpSchema.Implementation("emp-crud-mcp-chat-client", "1.0.0"))
                        .requestTimeout(Duration.ofSeconds(30))
                        .build();
                built.initialize();
                log.info("[MCP-CHAT] connected to {} - server: {}", serverBaseUrl, built.getServerInfo());
                mcpSyncClient = built;
            }
            return mcpSyncClient;
        }
    }

    private String stripAssistantPrefix(String answer) {
        if (answer == null) {
            return answer;
        }
        String trimmed = answer.strip();
        if (trimmed.regionMatches(true, 0, "assistant:", 0, "assistant:".length())) {
            return trimmed.substring("assistant:".length()).strip();
        }
        return trimmed;
    }

    private String formatHistory(List<ChatMessage> history) {
        if (history.isEmpty()) {
            return "No previous conversation.";
        }
        return history.stream()
                .map(item -> item.role().name() + ": " + item.content())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No previous conversation.");
    }
}
