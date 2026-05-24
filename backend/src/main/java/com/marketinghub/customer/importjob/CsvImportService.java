package com.marketinghub.customer.importjob;

import com.marketinghub.auth.AuthenticatedPrincipal;
import com.marketinghub.customer.importjob.dto.ImportJobDto;
import com.marketinghub.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class CsvImportService {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024; // 10 MB cap

    private final CsvImportJobRepository jobRepository;
    private final CsvImportProcessor processor;

    public CsvImportService(CsvImportJobRepository jobRepository, CsvImportProcessor processor) {
        this.jobRepository = jobRepository;
        this.processor = processor;
    }

    @Transactional
    public ImportJobDto startImport(MultipartFile file) {
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

        CsvImportJob job = new CsvImportJob();
        job.setTenantId(tenantId);
        job.setCreatedByUserId(userId);
        job.setFileName(file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename());
        job.setStatus(ImportJobStatus.PENDING);
        CsvImportJob saved = jobRepository.save(job);
        jobRepository.flush();
        UUID jobId = saved.getId();

        // The processor runs on a separate thread + connection — it must only fire
        // after this transaction commits so the new row is visible to its lookups.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    processor.process(jobId, bytes);
                }
            });
        } else {
            processor.process(jobId, bytes);
        }

        return ImportJobDto.of(saved);
    }

    @Transactional(readOnly = true)
    public ImportJobDto get(UUID jobId) {
        UUID tenantId = requireTenant();
        return jobRepository.findByIdAndTenantId(jobId, tenantId)
            .map(ImportJobDto::of)
            .orElseThrow(() -> new ImportJobNotFoundException(jobId));
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context");
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
}
