package com.practices.ai.embedding.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.practices.ai.embedding.KnowledgeDocumentService;
import com.practices.ai.embedding.dto.DocumentRequest;
import com.practices.ai.embedding.dto.DocumentResponse;
import com.practices.entity.KnowledgeDocument;

@CrossOrigin
@RestController
@RequestMapping("/api/ai/documents")
public class KnowledgeDocumentController {
    private final KnowledgeDocumentService documentService;

    public KnowledgeDocumentController(KnowledgeDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> create(@RequestBody DocumentRequest request) {
        KnowledgeDocument saved = documentService.create(
                request == null ? null : request.title(),
                request == null ? null : request.content(),
                request == null ? null : request.employeeId());
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> list() {
        return ResponseEntity.ok(documentService.findAll().stream().map(this::toResponse).toList());
    }

    private DocumentResponse toResponse(KnowledgeDocument document) {
        return new DocumentResponse(
                document.getId(), document.getTitle(), document.getContent(), document.getEmployeeId(), document.getCreatedAt());
    }
}
