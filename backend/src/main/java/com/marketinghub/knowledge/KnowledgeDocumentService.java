package com.marketinghub.knowledge;

import com.marketinghub.auth.AuthenticatedPrincipal;
import com.marketinghub.knowledge.dto.KnowledgeDocumentDto;
import com.marketinghub.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentService.class);
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private final KnowledgeDocumentRepository documentRepository;
    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;

    public KnowledgeDocumentService(KnowledgeDocumentRepository documentRepository, VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
        // TokenTextSplitter defaults (800 tokens) are fine for WhatsApp-sized answers.
        this.splitter = new TokenTextSplitter();
    }

    @Transactional
    public KnowledgeDocumentDto upload(MultipartFile file, String title) {
        UUID tenantId = requireTenant();
        UUID userId = currentUserId();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("File too large (max " + MAX_FILE_BYTES + " bytes)");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Could not read uploaded file", e);
        }

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTenantId(tenantId);
        doc.setTitle(title == null || title.isBlank() ? file.getOriginalFilename() : title);
        doc.setFileName(file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        doc.setStatus(KnowledgeDocumentStatus.PROCESSING);
        doc.setCreatedByUserId(userId);
        documentRepository.save(doc);
        documentRepository.flush();

        try {
            int chunkCount = ingest(bytes, doc);
            doc.setChunkCount(chunkCount);
            doc.setStatus(KnowledgeDocumentStatus.PROCESSED);
            log.info("Knowledge document {} ingested ({} chunks)", doc.getId(), chunkCount);
        } catch (Exception e) {
            log.warn("Knowledge document {} failed to ingest: {}", doc.getId(), e.getMessage(), e);
            doc.setStatus(KnowledgeDocumentStatus.FAILED);
            doc.setErrorMessage(truncate(e.getMessage(), 1000));
        }
        return KnowledgeDocumentDto.of(doc);
    }

    private int ingest(byte[] bytes, KnowledgeDocument doc) {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return doc.getFileName(); }
        };
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> raw = reader.read();
        List<Document> chunks = splitter.apply(raw);
        if (chunks.isEmpty()) {
            return 0;
        }
        List<Document> tagged = new ArrayList<>(chunks.size());
        for (Document c : chunks) {
            Map<String, Object> md = new HashMap<>();
            if (c.getMetadata() != null) md.putAll(c.getMetadata());
            md.put("tenant_id", doc.getTenantId().toString());
            md.put("document_id", doc.getId().toString());
            md.put("document_title", doc.getTitle());
            tagged.add(new Document(c.getText(), md));
        }
        vectorStore.add(tagged);
        return tagged.size();
    }

    @Transactional(readOnly = true)
    public Page<KnowledgeDocumentDto> list(int page, int size) {
        UUID tenantId = requireTenant();
        return documentRepository
            .findAllByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(page, size))
            .map(KnowledgeDocumentDto::of);
    }

    @Transactional(readOnly = true)
    public KnowledgeDocumentDto get(UUID id) {
        UUID tenantId = requireTenant();
        return documentRepository.findByIdAndTenantId(id, tenantId)
            .map(KnowledgeDocumentDto::of)
            .orElseThrow(() -> new KnowledgeDocumentNotFoundException(id));
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = requireTenant();
        KnowledgeDocument doc = documentRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new KnowledgeDocumentNotFoundException(id));
        // Best-effort delete from vector store; failures are logged but don't block the DB delete.
        try {
            vectorStore.delete("document_id == '" + doc.getId() + "'");
        } catch (Exception e) {
            log.warn("Failed to delete vectors for document {}: {}", id, e.getMessage());
        }
        documentRepository.delete(doc);
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context — knowledge base is tenant-scoped");
        }
        return tenantId;
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new AccessDeniedException("Not authenticated");
        }
        return principal.userId();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
