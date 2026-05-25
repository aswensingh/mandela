package com.marketinghub.knowledge;

import com.marketinghub.knowledge.dto.KnowledgeDocumentContent;
import com.marketinghub.knowledge.dto.KnowledgeDocumentDto;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge-documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService service;

    public KnowledgeDocumentController(KnowledgeDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public Page<KnowledgeDocumentDto> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    public KnowledgeDocumentDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<KnowledgeDocumentDto> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "title", required = false) String title
    ) {
        KnowledgeDocumentDto created = service.upload(file, title);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /**
     * Streams the original uploaded file back to the caller. Tenant-scoped — the
     * service throws {@code KnowledgeDocumentNotFoundException} (mapped to 404) for
     * cross-tenant attempts and for legacy rows uploaded before V14 (which never
     * stored the source bytes). Authenticated users in the tenant may download —
     * agents and viewers included, since the doc itself isn't sensitive admin data.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        KnowledgeDocumentContent content = service.download(id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.contentType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        // ContentDisposition's filename + filename* encoding is required to support
        // non-ASCII filenames (Chinese, accented chars) — without it, browsers fall
        // back to the URL slug and you end up with files called "download" everywhere.
        ContentDisposition disposition = ContentDisposition.attachment()
            .filename(content.fileName(), StandardCharsets.UTF_8)
            .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        headers.setContentType(mediaType);
        headers.setContentLength(content.size());
        return new ResponseEntity<>(content.bytes(), headers, HttpStatus.OK);
    }
}
