package com.marketinghub.tenant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findByWhatsappPhoneNumberId(String whatsappPhoneNumberId);

    // Excludes soft-deleted tenants — used by the default "list tenants" view.
    Page<Tenant> findAllByStatusNot(TenantStatus excluded, Pageable pageable);
}
