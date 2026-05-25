package com.marketinghub.knowledge;

import com.marketinghub.knowledge.dto.KnowledgeDocumentContent;
import com.marketinghub.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Focused unit tests for {@link KnowledgeDocumentService#download} — the only new
 * behaviour added by V14. Upload + ingest paths are exercised end-to-end by the
 * RAG integration tests; this file just nails down the download authorization,
 * legacy-row 404, and content-type fallback paths.
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentDownloadTest {

    @Mock private KnowledgeDocumentRepository documentRepository;
    @Mock private VectorStore vectorStore;

    private KnowledgeDocumentService service;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new KnowledgeDocumentService(documentRepository, vectorStore);
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void download_returnsBytesAndMetadata_forOwnTenant() {
        UUID id = UUID.randomUUID();
        byte[] bytes = "fake-pdf-bytes".getBytes();
        KnowledgeDocument doc = stubDoc(id, TENANT_A);
        doc.setContent(bytes);
        doc.setContentType("application/pdf");
        doc.setContentSize((long) bytes.length);
        when(documentRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(doc));

        KnowledgeDocumentContent content = service.download(id);

        assertThat(content.bytes()).isEqualTo(bytes);
        assertThat(content.fileName()).isEqualTo("price-list.pdf");
        assertThat(content.contentType()).isEqualTo("application/pdf");
        assertThat(content.size()).isEqualTo(bytes.length);
    }

    @Test
    void download_fallsBackToOctetStream_whenContentTypeMissing() {
        UUID id = UUID.randomUUID();
        KnowledgeDocument doc = stubDoc(id, TENANT_A);
        doc.setContent("x".getBytes());
        doc.setContentType(null);  // legacy / odd upload
        when(documentRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(doc));

        assertThat(service.download(id).contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void download_throwsNotFound_forCrossTenantId() {
        UUID id = UUID.randomUUID();
        // Document exists but belongs to Tenant B — repo's tenant-scoped query returns empty.
        when(documentRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(id))
            .isInstanceOf(KnowledgeDocumentNotFoundException.class);
    }

    @Test
    void download_throwsNotFound_forLegacyRowWithNullContent() {
        UUID id = UUID.randomUUID();
        KnowledgeDocument doc = stubDoc(id, TENANT_A);
        doc.setContent(null);  // uploaded before V14
        when(documentRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.download(id))
            .isInstanceOf(KnowledgeDocumentNotFoundException.class);
    }

    @Test
    void download_throwsNotFound_forEmptyContent() {
        UUID id = UUID.randomUUID();
        KnowledgeDocument doc = stubDoc(id, TENANT_A);
        doc.setContent(new byte[0]);  // edge case: zero-byte stored
        when(documentRepository.findByIdAndTenantId(id, TENANT_A)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.download(id))
            .isInstanceOf(KnowledgeDocumentNotFoundException.class);
    }

    private static KnowledgeDocument stubDoc(UUID id, UUID tenantId) {
        KnowledgeDocument d = new KnowledgeDocument();
        d.setId(id);
        d.setTenantId(tenantId);
        d.setTitle("Price list");
        d.setFileName("price-list.pdf");
        d.setStatus(KnowledgeDocumentStatus.PROCESSED);
        d.setChunkCount(3);
        d.setCreatedByUserId(UUID.randomUUID());
        return d;
    }
}
