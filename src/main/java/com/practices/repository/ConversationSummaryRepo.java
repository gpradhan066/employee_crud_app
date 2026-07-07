package com.practices.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.practices.entity.ConversationSummary;

public interface ConversationSummaryRepo extends JpaRepository<ConversationSummary, String> {
}
