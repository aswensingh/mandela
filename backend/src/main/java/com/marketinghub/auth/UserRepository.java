package com.marketinghub.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);
    Page<User> findAllByTenantId(UUID tenantId, Pageable pageable);
    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
    boolean existsByRole(UserRole role);

    // Batch load all users with a given role across a set of tenants. Used to populate the
    // 'admins' column on the Tenants list without an N+1 round-trip.
    List<User> findAllByTenantIdInAndRoleAndStatus(
        Collection<UUID> tenantIds, UserRole role, UserStatus status);
}
