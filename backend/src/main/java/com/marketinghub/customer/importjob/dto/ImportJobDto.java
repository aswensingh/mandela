package com.marketinghub.customer.importjob.dto;

import com.marketinghub.customer.importjob.CsvImportJob;
import com.marketinghub.customer.importjob.ImportJobStatus;
import com.marketinghub.customer.importjob.ImportRowError;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImportJobDto(
    UUID id,
    String fileName,
    ImportJobStatus status,
    int totalRows,
    int processedRows,
    int failedRows,
    List<ImportRowError> errorLog,
    Instant createdAt,
    Instant completedAt
) {
    public static ImportJobDto of(CsvImportJob j) {
        return new ImportJobDto(
            j.getId(),
            j.getFileName(),
            j.getStatus(),
            j.getTotalRows(),
            j.getProcessedRows(),
            j.getFailedRows(),
            j.getErrorLog(),
            j.getCreatedAt(),
            j.getCompletedAt()
        );
    }
}
