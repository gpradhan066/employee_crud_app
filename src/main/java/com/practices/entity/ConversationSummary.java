package com.practices.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "conversation_summary")
@Data
public class ConversationSummary {

    @Id
    @Column(length = 64)
    private String conversationId;

    @Column(nullable = false, columnDefinition = "text")
    private String summaryText;

    @Column(nullable = false)
    private int summarizedMessageCount;

    @Column(nullable = false)
    private Instant updatedAt;
}
