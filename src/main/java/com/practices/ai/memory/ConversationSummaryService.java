package com.practices.ai.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.practices.ai.config.AiProperties;
import com.practices.entity.ConversationMessage;
import com.practices.entity.ConversationSummary;
import com.practices.repository.ConversationMessageRepo;
import com.practices.repository.ConversationSummaryRepo;

/**
 * Folds older conversation turns into a rolling summary so {@link PersistentConversationMemory}
 * can keep feeding the model a small sliding window instead of an unbounded transcript, while the
 * full transcript stays in {@code conversation_message} for session view/audit (see doc/memory.md).
 *
 * <p>Runs on a background thread, never on the request thread that triggered it - the local Ollama
 * model is slow (seconds to minutes per call, per doc/agent.md), and a chat reply must not be held
 * up by summarizing turns nobody is waiting on right now. The summary is therefore eventually
 * consistent: it may lag by one triggering append, which is an accepted tradeoff for
 * responsiveness, not an oversight.</p>
 */
@Service
public class ConversationSummaryService {
    private static final Logger log = LoggerFactory.getLogger(ConversationSummaryService.class);

    private final ChatClient chatClient;
    private final Resource summaryPrompt;
    private final ConversationMessageRepo messageRepo;
    private final ConversationSummaryRepo summaryRepo;
    private final AiProperties aiProperties;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "conversation-summarizer");
        thread.setDaemon(true);
        return thread;
    });

    public ConversationSummaryService(
            ChatClient chatClient,
            @Value("classpath:/prompts/conversation-summary.st") Resource summaryPrompt,
            ConversationMessageRepo messageRepo,
            ConversationSummaryRepo summaryRepo,
            AiProperties aiProperties) {
        this.chatClient = chatClient;
        this.summaryPrompt = summaryPrompt;
        this.messageRepo = messageRepo;
        this.summaryRepo = summaryRepo;
        this.aiProperties = aiProperties;
    }

    public void updateSummaryAsync(String conversationId, long totalMessages) {
        executor.submit(() -> {
            try {
                updateSummary(conversationId, totalMessages);
            } catch (Exception exception) {
                log.warn("[MEMORY][SUMMARY] conversationId={} summarization failed - the sliding "
                        + "window still works, only the long-range summary is stale", conversationId, exception);
            }
        });
    }

    private void updateSummary(String conversationId, long totalMessages) {
        int keepRecent = aiProperties.maxHistoryMessages();
        long targetCovered = totalMessages - keepRecent;
        if (targetCovered <= 0) {
            return;
        }

        Optional<ConversationSummary> existing = summaryRepo.findById(conversationId);
        int alreadyCovered = existing.map(ConversationSummary::getSummarizedMessageCount).orElse(0);
        if (targetCovered <= alreadyCovered) {
            return;
        }

        List<ConversationMessage> all = messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId);
        int cappedTarget = (int) Math.min(targetCovered, all.size());
        List<ConversationMessage> newBatch = all.subList(alreadyCovered, cappedTarget);
        if (newBatch.isEmpty()) {
            return;
        }

        String renderedPrompt = new PromptTemplate(summaryPrompt).render(Map.of(
                "previousSummary", existing.map(ConversationSummary::getSummaryText).orElse("None."),
                "newMessages", renderMessages(newBatch)));

        String summaryText = chatClient.prompt()
                .user(renderedPrompt)
                .options(OllamaChatOptions.builder().model(aiProperties.defaultModel()).disableThinking())
                .call()
                .content();

        ConversationSummary summary = existing.orElseGet(ConversationSummary::new);
        summary.setConversationId(conversationId);
        summary.setSummaryText(summaryText == null || summaryText.isBlank() ? "(no summary produced)" : summaryText.strip());
        summary.setSummarizedMessageCount(cappedTarget);
        summary.setUpdatedAt(Instant.now());
        summaryRepo.save(summary);

        log.info("[MEMORY][SUMMARY] conversationId={} summarized {} of {} messages", conversationId, cappedTarget, totalMessages);
    }

    private String renderMessages(List<ConversationMessage> messages) {
        return messages.stream()
                .map(message -> message.getRole() + ": " + message.getContent())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
