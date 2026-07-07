package com.practices.ai.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.practices.ai.config.AiProperties;
import com.practices.ai.memory.ChatMessage.Role;
import com.practices.entity.ConversationMessage;
import com.practices.entity.ConversationSummary;
import com.practices.repository.ConversationMessageRepo;
import com.practices.repository.ConversationSummaryRepo;

/**
 * Postgres-backed {@link ConversationMemory} - every message is persisted permanently in
 * {@code conversation_message} (indexed on {@code (conversationId, createdAt)} for fast ordered
 * retrieval), so conversations survive an application restart and are available for session
 * management (see {@code com.practices.ai.memory.ConversationSessionQueryService}).
 *
 * <p>{@link #get(String)} only returns a bounded sliding window (the most recent
 * {@code app.ai.max-history-messages}) plus, once a conversation grows past
 * {@code app.ai.summary-trigger-messages}, a synthesized summary of everything older than the
 * window - see {@link ConversationSummaryService}. This is completely transparent to every caller
 * of {@link ConversationMemory} ({@code AIService}, {@code PolicyRagService},
 * {@code EmployeeAgentService}, {@code McpChatService}): they still just get a
 * {@code List<ChatMessage>} back and format it the same way they always did.</p>
 */
@Component
public class PersistentConversationMemory implements ConversationMemory {
    private final ConversationMessageRepo messageRepo;
    private final ConversationSummaryRepo summaryRepo;
    private final ConversationSummaryService summaryService;
    private final int windowSize;
    private final int summaryTriggerMessages;

    public PersistentConversationMemory(
            ConversationMessageRepo messageRepo,
            ConversationSummaryRepo summaryRepo,
            ConversationSummaryService summaryService,
            AiProperties properties) {
        this.messageRepo = messageRepo;
        this.summaryRepo = summaryRepo;
        this.summaryService = summaryService;
        this.windowSize = properties.maxHistoryMessages();
        this.summaryTriggerMessages = properties.summaryTriggerMessages();
    }

    @Override
    public List<ChatMessage> get(String conversationId) {
        List<ConversationMessage> recentDesc = messageRepo.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, windowSize));

        List<ChatMessage> window = new ArrayList<>(recentDesc.stream()
                .sorted(Comparator.comparing(ConversationMessage::getCreatedAt))
                .map(this::toChatMessage)
                .toList());

        summaryRepo.findById(conversationId).ifPresent(summary -> window.add(0, toSummaryMessage(summary)));

        return List.copyOf(window);
    }

    @Override
    public void append(String conversationId, ChatMessage message) {
        ConversationMessage row = new ConversationMessage();
        row.setConversationId(conversationId);
        row.setRole(message.role().name());
        row.setContent(message.content());
        row.setCreatedAt(message.createdAt());
        messageRepo.save(row);

        long total = messageRepo.countByConversationId(conversationId);
        if (total > summaryTriggerMessages) {
            summaryService.updateSummaryAsync(conversationId, total);
        }
    }

    @Override
    @Transactional
    public void clear(String conversationId) {
        messageRepo.deleteByConversationId(conversationId);
        if (summaryRepo.existsById(conversationId)) {
            summaryRepo.deleteById(conversationId);
        }
    }

    private ChatMessage toChatMessage(ConversationMessage row) {
        return new ChatMessage(Role.valueOf(row.getRole()), row.getContent(), row.getCreatedAt());
    }

    private ChatMessage toSummaryMessage(ConversationSummary summary) {
        return new ChatMessage(Role.ASSISTANT,
                "Summary of earlier conversation: " + summary.getSummaryText(),
                summary.getUpdatedAt());
    }
}
