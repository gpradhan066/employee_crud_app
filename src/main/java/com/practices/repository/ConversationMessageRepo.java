package com.practices.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.practices.entity.ConversationMessage;

public interface ConversationMessageRepo extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    List<ConversationMessage> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

    long countByConversationId(String conversationId);

    void deleteByConversationId(String conversationId);

    ConversationMessage findFirstByConversationIdAndRoleOrderByCreatedAtAsc(String conversationId, String role);

    List<ConversationMessage> findByRoleOrderByCreatedAtDesc(String role, Pageable pageable);

    @Query("select m.conversationId as conversationId, min(m.createdAt) as startedAt, "
            + "max(m.createdAt) as lastActivityAt, count(m) as messageCount "
            + "from ConversationMessage m group by m.conversationId order by max(m.createdAt) desc")
    List<SessionAggregate> findSessionAggregates();

    interface SessionAggregate {
        String getConversationId();
        Instant getStartedAt();
        Instant getLastActivityAt();
        long getMessageCount();
    }
}
