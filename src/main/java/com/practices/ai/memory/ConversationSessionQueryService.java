package com.practices.ai.memory;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.practices.ai.memory.dto.RecentQuestionResponse;
import com.practices.ai.memory.dto.SessionMessageResponse;
import com.practices.ai.memory.dto.SessionSummaryResponse;
import com.practices.entity.ConversationMessage;
import com.practices.repository.ConversationMessageRepo;
import com.practices.repository.ConversationMessageRepo.SessionAggregate;

/**
 * Read-side "session management" over the persisted conversation store - list every known
 * conversation, view one in full, or delete it - plus a cross-conversation "recent questions"
 * view. All derived directly from {@code conversation_message}; no separate session table is
 * needed since the conversationId already is the session identifier throughout this app.
 */
@Service
public class ConversationSessionQueryService {
    private static final String USER_ROLE = "USER";
    private static final int TITLE_MAX_LENGTH = 80;

    private final ConversationMessageRepo messageRepo;
    private final ConversationMemory memory;

    public ConversationSessionQueryService(ConversationMessageRepo messageRepo, ConversationMemory memory) {
        this.messageRepo = messageRepo;
        this.memory = memory;
    }

    public List<SessionSummaryResponse> listSessions(int limit) {
        return messageRepo.findSessionAggregates().stream()
                .limit(limit)
                .map(this::toSummary)
                .toList();
    }

    public List<SessionMessageResponse> getMessages(String conversationId) {
        return messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(message -> new SessionMessageResponse(message.getRole(), message.getContent(), message.getCreatedAt()))
                .toList();
    }

    public void deleteSession(String conversationId) {
        memory.clear(conversationId);
    }

    public List<RecentQuestionResponse> recentQuestions(int limit) {
        return messageRepo.findByRoleOrderByCreatedAtDesc(USER_ROLE, PageRequest.of(0, limit)).stream()
                .map(message -> new RecentQuestionResponse(
                        message.getConversationId(), message.getContent(), message.getCreatedAt()))
                .toList();
    }

    private SessionSummaryResponse toSummary(SessionAggregate aggregate) {
        return new SessionSummaryResponse(
                aggregate.getConversationId(),
                title(aggregate.getConversationId()),
                aggregate.getMessageCount(),
                aggregate.getStartedAt(),
                aggregate.getLastActivityAt());
    }

    private String title(String conversationId) {
        ConversationMessage first = messageRepo.findFirstByConversationIdAndRoleOrderByCreatedAtAsc(conversationId, USER_ROLE);
        if (first == null || first.getContent() == null || first.getContent().isBlank()) {
            return "New conversation";
        }
        String content = first.getContent().strip();
        return content.length() > TITLE_MAX_LENGTH ? content.substring(0, TITLE_MAX_LENGTH) + "..." : content;
    }
}
