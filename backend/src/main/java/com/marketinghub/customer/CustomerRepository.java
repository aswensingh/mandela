package com.marketinghub.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Customer> findByTenantIdAndPhoneE164(UUID tenantId, String phoneE164);

    boolean existsByTenantIdAndPhoneE164(UUID tenantId, String phoneE164);

    long countByIdInAndTenantId(Collection<UUID> ids, UUID tenantId);

    List<Customer> findAllByIdInAndTenantId(Collection<UUID> ids, UUID tenantId);

    /**
     * Bulk delete scoped to a tenant in a single SQL statement (no per-row entity load).
     * Returns the number of rows actually deleted — ids belonging to other tenants are
     * filtered out by the tenant_id predicate, so cross-tenant ids are silently ignored.
     */
    @Modifying
    @Query("DELETE FROM Customer c WHERE c.tenantId = :tenantId AND c.id IN :ids")
    int deleteByTenantIdAndIdIn(@Param("tenantId") UUID tenantId, @Param("ids") Collection<UUID> ids);

    /**
     * Tenant-scoped search with optional filters.
     *  - search: ILIKE on phone_e164 OR full_name (nullable)
     *  - tag: must appear in the tags array (nullable; native to leverage Postgres array)
     *  - optInStatus: exact match on opt_in_status (nullable)
     */
    @Query(
        value = """
            SELECT * FROM customers
            WHERE tenant_id = :tenantId
              AND (CAST(:search AS text) IS NULL
                   OR phone_e164 ILIKE '%' || :search || '%'
                   OR full_name  ILIKE '%' || :search || '%')
              AND (CAST(:tag AS text) IS NULL OR :tag = ANY(tags))
              AND (CAST(:optInStatus AS text) IS NULL OR opt_in_status = :optInStatus)
            """,
        countQuery = """
            SELECT count(*) FROM customers
            WHERE tenant_id = :tenantId
              AND (CAST(:search AS text) IS NULL
                   OR phone_e164 ILIKE '%' || :search || '%'
                   OR full_name  ILIKE '%' || :search || '%')
              AND (CAST(:tag AS text) IS NULL OR :tag = ANY(tags))
              AND (CAST(:optInStatus AS text) IS NULL OR opt_in_status = :optInStatus)
            """,
        nativeQuery = true)
    Page<Customer> search(
        @Param("tenantId") UUID tenantId,
        @Param("search") String search,
        @Param("tag") String tag,
        @Param("optInStatus") String optInStatus,
        Pageable pageable);
}
