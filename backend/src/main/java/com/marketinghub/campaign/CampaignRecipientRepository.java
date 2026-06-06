package com.marketinghub.campaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, UUID> {

    long countByCampaignId(UUID campaignId);

    /** Used by the delivery-status webhook to map a Meta wamid back to its recipient row. */
    Optional<CampaignRecipient> findByWhatsappMessageId(String whatsappMessageId);

    long countByCampaignIdAndStatus(UUID campaignId, CampaignRecipientStatus status);

    Page<CampaignRecipient> findAllByCampaignId(UUID campaignId, Pageable pageable);

    List<CampaignRecipient> findAllByCampaignIdOrderByCreatedAtAsc(UUID campaignId);
}
