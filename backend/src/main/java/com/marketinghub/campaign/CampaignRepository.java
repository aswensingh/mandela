package com.marketinghub.campaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    Optional<Campaign> findByIdAndTenantId(UUID id, UUID tenantId);
    Page<Campaign> findAllByTenantId(UUID tenantId, Pageable pageable);
}
