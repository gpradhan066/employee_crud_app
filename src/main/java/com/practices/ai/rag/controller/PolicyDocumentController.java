package com.practices.ai.rag.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.practices.ai.rag.PolicyDocumentIngestionService;
import com.practices.ai.rag.dto.PolicyDocumentResponse;
import com.practices.entity.PolicyDocument;

@CrossOrigin
@RestController
@RequestMapping("/api/ai/policy-documents")
public class PolicyDocumentController {
    private final PolicyDocumentIngestionService ingestionService;

    public PolicyDocumentController(PolicyDocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PolicyDocumentResponse> upload(
            @RequestParam("file") MultipartFile file, @RequestParam("category") String category) {
        PolicyDocument saved = ingestionService.upload(file, category);
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping
    public ResponseEntity<List<PolicyDocumentResponse>> list() {
        return ResponseEntity.ok(ingestionService.findAll().stream().map(this::toResponse).toList());
    }

    private PolicyDocumentResponse toResponse(PolicyDocument document) {
        return new PolicyDocumentResponse(
                document.getId(), document.getFilename(), document.getCategory(), document.getChunkCount(), document.getUploadedAt());
    }
}
