package com.practices.ai.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.practices.entity.PolicyDocument;
import com.practices.repository.PolicyDocumentRepo;

/**
 * Upload -&gt; extract -&gt; split -&gt; embed -&gt; store pipeline for RAG source documents (PDF/DOCX/TXT).
 *
 * <p>Reuses the exact same {@link VectorStore} bean (and therefore the same {@code vector_embeddings}
 * pgvector table) that {@link com.practices.ai.embedding.EmbeddingIngestionService} already populates
 * for employees and free-text {@code KnowledgeDocument} notes - see {@code doc/rag.md}. Chunks are
 * tagged {@code type=policy_chunk} so retrieval for policy Q&amp;A (see {@link PolicyRagService}) can be
 * scoped to only this content, and so the general semantic-search feature can still tell them apart
 * from employee/document results.</p>
 */
@Service
public class PolicyDocumentIngestionService {
    private static final Logger log = LoggerFactory.getLogger(PolicyDocumentIngestionService.class);

    public static final String TYPE_POLICY_CHUNK = "policy_chunk";
    public static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "Company Policy", "HR Handbook", "Leave Policy", "Insurance Policy");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt");

    private final VectorStore vectorStore;
    private final PolicyDocumentRepo policyDocumentRepo;
    private final TokenTextSplitter textSplitter = new TokenTextSplitter();

    public PolicyDocumentIngestionService(VectorStore vectorStore, PolicyDocumentRepo policyDocumentRepo) {
        this.vectorStore = vectorStore;
        this.policyDocumentRepo = policyDocumentRepo;
    }

    public PolicyDocument upload(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A file is required.");
        }
        String filename = file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                ? file.getOriginalFilename() : "document";
        String extension = extensionOf(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported file type - only PDF, DOCX, and TXT are allowed.");
        }
        if (category == null || !ALLOWED_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Category must be one of: " + String.join(", ", ALLOWED_CATEGORIES));
        }

        List<Document> extracted = extractText(file, filename);
        boolean hasReadableText = extracted.stream()
                .anyMatch(document -> document.getText() != null && !document.getText().isBlank());
        if (!hasReadableText) {
            throw new IllegalArgumentException("No readable text could be extracted from this file.");
        }

        PolicyDocument policyDocument = new PolicyDocument();
        policyDocument.setFilename(filename);
        policyDocument.setCategory(category);
        policyDocument.setUploadedAt(Instant.now());
        policyDocument.setChunkCount(0);
        policyDocument = policyDocumentRepo.save(policyDocument);

        for (Document document : extracted) {
            document.getMetadata().put("type", TYPE_POLICY_CHUNK);
            document.getMetadata().put("policyDocumentId", policyDocument.getId());
            document.getMetadata().put("filename", filename);
            document.getMetadata().put("category", category);
        }

        List<Document> chunks = textSplitter.apply(extracted);
        List<Document> taggedChunks = new ArrayList<>();
        int index = 0;
        for (Document chunk : chunks) {
            taggedChunks.add(Document.builder()
                    .id(deterministicChunkId(policyDocument.getId(), index))
                    .text(chunk.getText())
                    .metadata(chunk.getMetadata())
                    .build());
            index++;
        }

        if (!taggedChunks.isEmpty()) {
            vectorStore.add(taggedChunks);
        }

        policyDocument.setChunkCount(taggedChunks.size());
        policyDocument = policyDocumentRepo.save(policyDocument);

        log.info("[RAG] uploaded filename={} category={} policyDocumentId={} chunks={}",
                filename, category, policyDocument.getId(), taggedChunks.size());

        return policyDocument;
    }

    public List<PolicyDocument> findAll() {
        return policyDocumentRepo.findAll();
    }

    private List<Document> extractText(MultipartFile file, String filename) {
        try {
            byte[] bytes = file.getBytes();
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            return new TikaDocumentReader(resource).get();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read the uploaded file.", exception);
        }
    }

    private String deterministicChunkId(Long policyDocumentId, int index) {
        return UUID.nameUUIDFromBytes(("policy-chunk-" + policyDocumentId + "-" + index).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
