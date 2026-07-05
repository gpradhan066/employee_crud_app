package com.practices.ai.embedding;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.practices.ai.embedding.dto.ReindexResponse;
import com.practices.entity.Employee;
import com.practices.entity.KnowledgeDocument;
import com.practices.repository.EmployeeRepo;
import com.practices.repository.KnowledgeDocumentRepo;

/**
 * Keeps the pgvector-backed {@link VectorStore} in sync with {@link Employee} and
 * {@link KnowledgeDocument} rows.
 *
 * <p>Every embedded {@link Document} is given a stable id deterministically derived from its
 * logical key ("employee-{id}" / "document-{id}"), via {@link UUID#nameUUIDFromBytes} - PgVectorStore
 * requires document ids to be valid UUIDs, so a plain string key can't be used directly. Re-indexing
 * the same row therefore overwrites its existing vector instead of accumulating duplicates. A full
 * reindex additionally deletes-then-rebuilds each type so that rows removed from Postgres don't
 * linger as stale, orphaned vectors - see {@code doc/pgvector.md} for why this isn't wired into the
 * live CRUD write path.</p>
 */
@Service
public class EmbeddingIngestionService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingIngestionService.class);
    private static final String TYPE_EMPLOYEE = "employee";
    private static final String TYPE_DOCUMENT = "document";

    private final VectorStore vectorStore;
    private final EmployeeRepo employeeRepo;
    private final KnowledgeDocumentRepo documentRepo;

    public EmbeddingIngestionService(VectorStore vectorStore, EmployeeRepo employeeRepo, KnowledgeDocumentRepo documentRepo) {
        this.vectorStore = vectorStore;
        this.employeeRepo = employeeRepo;
        this.documentRepo = documentRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingestOnStartup() {
        // Must never throw: an ApplicationReadyEvent listener that throws aborts the entire
        // application startup (Spring Boot treats it as a fatal run failure), which would take
        // down unrelated features (core CRUD, chat, agent) over an indexing problem alone.
        try {
            ReindexResponse result = reindexAll();
            log.info("[EMBEDDING] startup reindex complete: employees={} documents={}",
                    result.employeesIndexed(), result.documentsIndexed());
        } catch (Exception exception) {
            log.warn("[EMBEDDING] startup reindex failed - semantic search may return stale or no results "
                    + "until POST /api/ai/semantic-search/reindex is called", exception);
        }
    }

    public ReindexResponse reindexAll() {
        return new ReindexResponse(reindexEmployees(), reindexDocuments());
    }

    public int reindexEmployees() {
        vectorStore.delete(new FilterExpressionBuilder().eq("type", TYPE_EMPLOYEE).build());
        List<Document> documents = employeeRepo.findAll().stream().map(this::toDocument).toList();
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
        log.info("[EMBEDDING] reindexed {} employees", documents.size());
        return documents.size();
    }

    public int reindexDocuments() {
        vectorStore.delete(new FilterExpressionBuilder().eq("type", TYPE_DOCUMENT).build());
        List<Document> documents = documentRepo.findAll().stream().map(this::toDocument).toList();
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
        log.info("[EMBEDDING] reindexed {} documents", documents.size());
        return documents.size();
    }

    public void indexDocument(KnowledgeDocument knowledgeDocument) {
        vectorStore.add(List.of(toDocument(knowledgeDocument)));
        log.info("[EMBEDDING] indexed document id={}", knowledgeDocument.getId());
    }

    private Document toDocument(Employee employee) {
        String roleClause = employee.getRole() != null && !employee.getRole().isBlank()
                ? " as a " + employee.getRole() : "";
        String text = "Employee %s works in the %s department%s, has a performance rating of %s and %s years of experience."
                .formatted(employee.getName(), employee.getDepartment(), roleClause,
                        employee.getPerformance(), employee.getExperience());

        return Document.builder()
                .id(deterministicId(TYPE_EMPLOYEE, employee.getId()))
                .text(text)
                .metadata("type", TYPE_EMPLOYEE)
                .metadata("referenceId", employee.getId())
                .metadata("name", employee.getName())
                .build();
    }

    private Document toDocument(KnowledgeDocument knowledgeDocument) {
        String text = knowledgeDocument.getTitle() + ". " + knowledgeDocument.getContent();
        Document.Builder builder = Document.builder()
                .id(deterministicId(TYPE_DOCUMENT, knowledgeDocument.getId()))
                .text(text)
                .metadata("type", TYPE_DOCUMENT)
                .metadata("referenceId", knowledgeDocument.getId())
                .metadata("title", knowledgeDocument.getTitle());
        if (knowledgeDocument.getEmployeeId() != null) {
            builder.metadata("employeeId", knowledgeDocument.getEmployeeId());
        }
        return builder.build();
    }

    private String deterministicId(String type, Long id) {
        return UUID.nameUUIDFromBytes((type + "-" + id).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
