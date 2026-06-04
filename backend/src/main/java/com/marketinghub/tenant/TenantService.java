package com.marketinghub.tenant;

import com.marketinghub.auth.User;
import com.marketinghub.auth.UserRepository;
import com.marketinghub.auth.UserRole;
import com.marketinghub.auth.UserStatus;
import com.marketinghub.auth.dto.UserDto;
import com.marketinghub.tenant.dto.CreateTenantRequest;
import com.marketinghub.tenant.dto.CreateTenantResponse;
import com.marketinghub.tenant.dto.TenantAdminDto;
import com.marketinghub.tenant.dto.TenantDto;
import com.marketinghub.tenant.dto.UpdateTenantRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public TenantService(
        TenantRepository tenantRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JdbcTemplate jdbcTemplate
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts the tenant and the initial TENANT_ADMIN in a single transaction —
     * if the user insert fails (e.g. uniqueness violation), the tenant insert rolls back.
     */
    @Transactional
    public CreateTenantResponse createTenant(CreateTenantRequest request) {
        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        tenant.setIndustry(request.industry());
        tenant.setStatus(TenantStatus.ACTIVE);
        Tenant savedTenant = tenantRepository.save(tenant);

        User admin = new User();
        admin.setUsername(request.initialAdminUsername());
        admin.setPasswordHash(passwordEncoder.encode(request.initialAdminPassword()));
        admin.setFullName(request.initialAdminUsername());
        admin.setRole(UserRole.TENANT_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setTenantId(savedTenant.getId());
        User savedAdmin = userRepository.save(admin);
        // Force INSERT to flush before tx commit so unique-violations surface here, not at commit time.
        userRepository.flush();

        return new CreateTenantResponse(
            toDto(savedTenant),
            new UserDto(
                savedAdmin.getId(),
                savedAdmin.getUsername(),
                savedAdmin.getFullName(),
                savedAdmin.getTenantId(),
                savedTenant.getName(),  // tenantName — we just created it
                savedAdmin.getRole()
            )
        );
    }

    @Transactional(readOnly = true)
    public Page<TenantDto> listTenants(Pageable pageable, boolean includeDeleted) {
        Page<Tenant> page = includeDeleted
            ? tenantRepository.findAll(pageable)
            : tenantRepository.findAllByStatusNot(TenantStatus.DELETED, pageable);
        if (page.isEmpty()) {
            return page.map(t -> toDto(t, List.of()));
        }
        Map<UUID, List<TenantAdminDto>> adminsByTenant = loadAdmins(
            page.stream().map(Tenant::getId).collect(Collectors.toSet()));
        return page.map(t -> toDto(t, adminsByTenant.getOrDefault(t.getId(), List.of())));
    }

    /**
     * Batch load ACTIVE TENANT_ADMIN users for the given tenants and group them by tenant_id.
     * Single SQL round-trip — avoids N+1 when rendering the Tenants list.
     */
    private Map<UUID, List<TenantAdminDto>> loadAdmins(Set<UUID> tenantIds) {
        List<User> admins = userRepository.findAllByTenantIdInAndRoleAndStatus(
            tenantIds, UserRole.TENANT_ADMIN, UserStatus.ACTIVE);
        Map<UUID, List<TenantAdminDto>> grouped = new HashMap<>();
        for (User u : admins) {
            grouped.computeIfAbsent(u.getTenantId(), k -> new ArrayList<>())
                .add(new TenantAdminDto(u.getId(), u.getUsername()));
        }
        return grouped;
    }

    @Transactional
    public TenantDto update(UUID id, UpdateTenantRequest request) {
        Tenant tenant = requireNotDeleted(id, "update");
        if (request.name() != null && !request.name().isBlank()) {
            tenant.setName(request.name());
        }
        if (request.industry() != null) {
            tenant.setIndustry(request.industry());
        }
        return toDto(tenant);
    }

    @Transactional
    public TenantDto suspend(UUID id) {
        Tenant tenant = requireNotDeleted(id, "suspend");
        tenant.setStatus(TenantStatus.SUSPENDED);
        return toDto(tenant);
    }

    @Transactional
    public TenantDto unsuspend(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new TenantNotFoundException(id));
        if (tenant.getStatus() == TenantStatus.DELETED) {
            throw new IllegalArgumentException(
                "Cannot unsuspend a DELETED tenant — soft-deleted tenants must be restored via a separate path (not yet implemented)");
        }
        tenant.setStatus(TenantStatus.ACTIVE);
        return toDto(tenant);
    }

    /**
     * Soft-delete: mark the tenant DELETED, revoke all active refresh tokens of its users so
     * existing sessions die on the next API call. Data stays in the DB. Reversible via a future
     * restore endpoint (out of scope here) — for now the row is just hidden from the default list.
     */
    @Transactional
    public TenantDto softDelete(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new TenantNotFoundException(id));
        if (tenant.getStatus() == TenantStatus.DELETED) {
            // Idempotent.
            return toDto(tenant);
        }
        tenant.setStatus(TenantStatus.DELETED);
        // Kill all live sessions for this tenant's users. Done via raw SQL so we don't pull every
        // user into memory just to issue per-user revokes. Convert Instant -> Timestamp so the
        // Postgres JDBC driver can bind it without a type hint.
        int revoked = jdbcTemplate.update(
            "UPDATE refresh_tokens SET revoked_at = ? "
                + "WHERE revoked_at IS NULL AND user_id IN (SELECT id FROM users WHERE tenant_id = ?)",
            Timestamp.from(Instant.now()), id);
        log.info("Tenant {} soft-deleted; revoked {} active refresh tokens", id, revoked);
        return toDto(tenant);
    }

    /**
     * Hard purge: completely remove the tenant and all its owned data. Requires the tenant to be
     * in DELETED state first — this is the second step of a two-step destructive action.
     *
     * Caller must pass {@code confirmName} matching the tenant's current name exactly — a typo
     * gate that mirrors the UI confirmation modal. Deletes child rows in FK-safe order:
     *
     * <pre>
     *   refresh_tokens (cascades when users go)
     *   campaign_recipients (cascades when campaigns go)
     *   messages
     *   conversations
     *   campaigns
     *   message_templates
     *   knowledge_documents
     *   vector_store (filter on metadata-&gt;&gt;'tenant_id')
     *   csv_import_jobs
     *   customers
     *   users
     *   tenants
     * </pre>
     */
    @Transactional
    public void purge(UUID id, String confirmName) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new TenantNotFoundException(id));
        if (tenant.getStatus() != TenantStatus.DELETED) {
            throw new IllegalArgumentException(
                "Tenant must be soft-deleted before purge — current status is " + tenant.getStatus());
        }
        if (confirmName == null || !Objects.equals(confirmName, tenant.getName())) {
            throw new IllegalArgumentException(
                "confirmName does not match tenant name — purge aborted");
        }

        String idStr = id.toString();
        // 1. Messages first (FK to customer_id which we'll delete later)
        int messages = jdbcTemplate.update("DELETE FROM messages WHERE tenant_id = ?", id);
        // 2. Conversations (FK to customer_id + users.assigned_agent_id)
        int conversations = jdbcTemplate.update("DELETE FROM conversations WHERE tenant_id = ?", id);
        // 3. Campaigns — campaign_recipients cascades via FK
        int campaigns = jdbcTemplate.update("DELETE FROM campaigns WHERE tenant_id = ?", id);
        // 4. Templates
        int templates = jdbcTemplate.update("DELETE FROM message_templates WHERE tenant_id = ?", id);
        // 5. Knowledge documents (the metadata rows)
        int docs = jdbcTemplate.update("DELETE FROM knowledge_documents WHERE tenant_id = ?", id);
        // 6. Vector store chunks for this tenant. tenant_id lives inside the JSON metadata column.
        //    PgVectorStore's filter expression is fine for reads; for a bulk delete we go direct.
        int chunks = jdbcTemplate.update(
            "DELETE FROM vector_store WHERE metadata->>'tenant_id' = ?", idStr);
        // 7. CSV import jobs
        int csvJobs = jdbcTemplate.update("DELETE FROM csv_import_jobs WHERE tenant_id = ?", id);
        // 8. Customers
        int customers = jdbcTemplate.update("DELETE FROM customers WHERE tenant_id = ?", id);
        // 9. Users (refresh_tokens cascades via ON DELETE CASCADE on user_id)
        int users = jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", id);
        // 10. Tenant row
        int tenants = jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", id);

        log.warn(
            "PURGE tenant {} ({}): {} messages, {} convos, {} campaigns, {} templates, "
                + "{} docs, {} vector chunks, {} csv jobs, {} customers, {} users, {} tenant rows",
            id, tenant.getName(),
            messages, conversations, campaigns, templates, docs, chunks, csvJobs, customers, users, tenants);
    }

    private Tenant requireNotDeleted(UUID id, String action) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new TenantNotFoundException(id));
        if (tenant.getStatus() == TenantStatus.DELETED) {
            throw new IllegalArgumentException(
                "Cannot " + action + " a DELETED tenant — restore it first or purge permanently");
        }
        return tenant;
    }

    /**
     * For single-tenant mutations (create / update / suspend / softDelete) we do a per-call
     * lookup of admins. The page-listing path uses the batched {@link #loadAdmins} instead.
     */
    private TenantDto toDto(Tenant tenant) {
        List<TenantAdminDto> admins = userRepository
            .findAllByTenantIdInAndRoleAndStatus(Set.of(tenant.getId()),
                UserRole.TENANT_ADMIN, UserStatus.ACTIVE)
            .stream()
            .map(u -> new TenantAdminDto(u.getId(), u.getUsername()))
            .collect(Collectors.toList());
        return toDto(tenant, admins);
    }

    static TenantDto toDto(Tenant tenant, List<TenantAdminDto> admins) {
        return new TenantDto(
            tenant.getId(),
            tenant.getName(),
            tenant.getIndustry(),
            tenant.getStatus(),
            tenant.getCreatedAt(),
            tenant.getUpdatedAt(),
            admins
        );
    }
}
