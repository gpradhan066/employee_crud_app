package com.practices.ai.embedding;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.practices.entity.KnowledgeDocument;
import com.practices.repository.KnowledgeDocumentRepo;

@Service
public class KnowledgeDocumentService {
    private final KnowledgeDocumentRepo documentRepo;
    private final EmbeddingIngestionService ingestionService;

    public KnowledgeDocumentService(KnowledgeDocumentRepo documentRepo, EmbeddingIngestionService ingestionService) {
        this.documentRepo = documentRepo;
        this.ingestionService = ingestionService;
    }

    public KnowledgeDocument create(String title, String content, Long employeeId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Document title is required.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Document content is required.");
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(title.trim());
        document.setContent(content.trim());
        document.setEmployeeId(employeeId);
        document.setCreatedAt(Instant.now());

        KnowledgeDocument saved = documentRepo.save(document);
        ingestionService.indexDocument(saved);
        return saved;
    }

    public List<KnowledgeDocument> findAll() {
        return documentRepo.findAll();
    }
}
