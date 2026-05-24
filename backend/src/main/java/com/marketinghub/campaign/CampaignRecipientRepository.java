package com.marketinghub.campaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, UUID> {

    long countByCampaignId(UUID campaignId);

    long countByCampaignIdAndStatus(UUID campaignId, CampaignRecipientStatus status);

    Page<CampaignRecipient> findAllByCampaignId(UUID campaignId, Pageable pageable);

    List<CampaignRecipient> findAllByCampaignIdOrderByCreatedAtAsc(UUID campaignId);
}
