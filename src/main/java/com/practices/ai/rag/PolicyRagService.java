package com.practices.ai.rag;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.practices.ai.config.AiProperties;
import com.practices.ai.controller.dto.ChatResponse;
import com.practices.ai.memory.ChatMessage;
import com.practices.ai.memory.ChatMessage.Role;
import com.practices.ai.memory.ConversationMemory;

/**
 * Retrieval-Augmented Generation over uploaded policy documents only.
 *
 * <p>Unlike the general Spring AI chat path ({@code AIService}), this service never lets the model
 * answer from general knowledge or call tools - it retrieves the most relevant
 * {@link PolicyDocumentIngestionService#TYPE_POLICY_CHUNK} chunks from the shared pgvector store, injects
 * them into the prompt as the only allowed source of truth, and short-circuits with a canned response
 * (no model call at all) when nothing relevant is found - see {@code doc/rag.md}.</p>
 */
@Service
public class PolicyRagService {
    private static final Logger log = LoggerFactory.getLogger(PolicyRagService.class);
    private static final int TOP_K = 5;
    private static final String MODEL_LABEL = "policy-rag";

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final Resource systemPrompt;
    private final Resource userPrompt;
    private final ConversationMemory memory;
    private final AiProperties aiProperties;

    public PolicyRagService(
            ChatClient chatClient,
            VectorStore vectorStore,
            @Value("classpath:/prompts/policy-rag-system.st") Resource systemPrompt,
            @Value("classpath:/prompts/employee-assistant-user.st") Resource userPrompt,
            ConversationMemory memory,
            AiProperties aiProperties) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.memory = memory;
        this.aiProperties = aiProperties;
    }

    public ChatResponse chat(String conversationId, String message, boolean think) {
        Instant now = Instant.now();
        memory.append(conversationId, new ChatMessage(Role.USER, message, now));

        List<Document> matches = vectorStore.similaritySearch(SearchRequest.builder()
                .query(message)
                .topK(TOP_K)
                .filterExpression(new FilterExpressionBuilder()
                        .eq("type", PolicyDocumentIngestionService.TYPE_POLICY_CHUNK)
                        .build())
                .build());

        log.info("[RAG] conversationId={} retrievedChunks={}", conversationId, matches.size());

        if (matches.isEmpty()) {
            String answer = "The uploaded policy documents don't cover that - either nothing has been "
                    + "uploaded yet, or nothing relevant to your question was found.";
            memory.append(conversationId, new ChatMessage(Role.ASSISTANT, answer, now));
            return new ChatResponse(conversationId, answer, MODEL_LABEL, now);
        }

        String renderedSystem = new PromptTemplate(systemPrompt).render(Map.of(
                "context", renderContext(matches),
                "history", formatHistory(memory.get(conversationId))));
        String renderedUser = new PromptTemplate(userPrompt).render(Map.of("message", message));

        OllamaChatOptions.Builder options = OllamaChatOptions.builder().model(aiProperties.defaultModel());
        if (think) {
            options.enableThinking();
        } else {
            options.disableThinking();
        }

        String answer = stripAssistantPrefix(chatClient.prompt()
                .system(renderedSystem)
                .user(renderedUser)
                .options(options)
                .call()
                .content());

        Instant answeredAt = Instant.now();
        memory.append(conversationId, new ChatMessage(Role.ASSISTANT, answer, answeredAt));

        return new ChatResponse(conversationId, answer, MODEL_LABEL, answeredAt);
    }

    private String renderContext(List<Document> matches) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Document document : matches) {
            Object category = document.getMetadata().get("category");
            Object filename = document.getMetadata().get("filename");
            sb.append(index++).append(". [").append(category).append(" - ").append(filename).append("]\n")
                    .append(document.getText()).append("\n\n");
        }
        return sb.toString();
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
