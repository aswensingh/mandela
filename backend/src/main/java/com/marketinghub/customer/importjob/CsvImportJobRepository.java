package com.marketinghub.customer.importjob;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CsvImportJobRepository extends JpaRepository<CsvImportJob, UUID> {
    Optional<CsvImportJob> findByIdAndTenantId(UUID id, UUID tenantId);
}
