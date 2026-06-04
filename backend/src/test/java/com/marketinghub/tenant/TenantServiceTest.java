package com.marketinghub.tenant;

import com.marketinghub.auth.RefreshTokenRepository;
import com.marketinghub.auth.User;
import com.marketinghub.auth.UserRepository;
import com.marketinghub.auth.UserRole;
import com.marketinghub.auth.UserStatus;
import com.marketinghub.auth.UserStatus;
import com.marketinghub.tenant.dto.CreateTenantRequest;
import com.marketinghub.tenant.dto.CreateTenantResponse;
import com.marketinghub.tenant.dto.TenantAdminDto;
import com.marketinghub.tenant.dto.TenantDto;
import com.marketinghub.tenant.dto.UpdateTenantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(
            tenantRepository, userRepository, passwordEncoder, jdbcTemplate);
    }

    @Test
    void createTenant_savesTenantAndInitialAdmin() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(tenantId);
            return t;
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        CreateTenantResponse response = tenantService.createTenant(new CreateTenantRequest(
            "Acme Dental", "dental", "acme", "supersecret"));

        assertThat(response.tenant().id()).isEqualTo(tenantId);
        assertThat(response.tenant().name()).isEqualTo("Acme Dental");
        assertThat(response.initialAdmin().username()).isEqualTo("acme");
        assertThat(response.initialAdmin().role()).isEqualTo(UserRole.TENANT_ADMIN);
        assertThat(response.initialAdmin().tenantId()).isEqualTo(tenantId);

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        User saved = userCap.getValue();
        assertThat(saved.getRole()).isEqualTo(UserRole.TENANT_ADMIN);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(passwordEncoder.matches("supersecret", saved.getPasswordHash())).isTrue();
    }

    @Test
    void createTenant_propagatesUserUniqueViolation_soOuterTxRollsBack() {
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(userRepository.save(any(User.class)))
            .thenThrow(new DataIntegrityViolationException("uk_violation"));

        assertThatThrownBy(() -> tenantService.createTenant(new CreateTenantRequest(
            "Acme Dental", "dental", "admin@acme.com", "supersecret")))
            .isInstanceOf(DataIntegrityViolationException.class);

        // tenant.save was called (and Spring's @Transactional will roll back the surrounding tx)
        verify(tenantRepository).save(any(Tenant.class));
        verify(userRepository, never()).flush();
    }

    @Test
    void update_changesNameAndIndustry_whenProvided() {
        UUID id = UUID.randomUUID();
        Tenant existing = new Tenant();
        existing.setId(id);
        existing.setName("Old");
        existing.setIndustry("old-industry");
        existing.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findById(id)).thenReturn(Optional.of(existing));

        TenantDto dto = tenantService.update(id, new UpdateTenantRequest("New Name", "casino"));

        assertThat(dto.name()).isEqualTo("New Name");
        assertThat(dto.industry()).isEqualTo("casino");
    }

    @Test
    void update_throws_whenTenantMissing() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tenantService.update(id, new UpdateTenantRequest("x", null)))
            .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void suspend_unsuspend_toggleStatus() {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName("Acme");
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

        TenantDto suspended = tenantService.suspend(id);
        assertThat(suspended.status()).isEqualTo(TenantStatus.SUSPENDED);

        TenantDto reactivated = tenantService.unsuspend(id);
        assertThat(reactivated.status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void softDelete_setsDeletedAndRevokesRefreshTokens() {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName("Acme");
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

        TenantDto dto = tenantService.softDelete(id);

        assertThat(dto.status()).isEqualTo(TenantStatus.DELETED);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.DELETED);
        // The UPDATE on refresh_tokens runs exactly once.
        verify(jdbcTemplate).update(contains("refresh_tokens"), any(Object[].class));
    }

    @Test
    void softDelete_idempotentOnAlreadyDeletedTenant() {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName("Acme");
        tenant.setStatus(TenantStatus.DELETED);
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

        TenantDto dto = tenantService.softDelete(id);

        assertThat(dto.status()).isEqualTo(TenantStatus.DELETED);
        // No second revoke run on idempotent call.
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void purge_rejectsTenantNotInDeletedState() {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName("Acme");
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> tenantService.purge(id, "Acme"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("soft-deleted before purge");

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void purge_rejectsMismatchedConfirmName() {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName("Acme");
        tenant.setStatus(TenantStatus.DELETED);
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> tenantService.purge(id, "Wrong"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("confirmName");

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void listTenants_populatesAdmins_perTenant() {
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        Tenant tenant1 = new Tenant();
        tenant1.setId(t1);
        tenant1.setName("Acme");
        tenant1.setStatus(TenantStatus.ACTIVE);
        Tenant tenant2 = new Tenant();
        tenant2.setId(t2);
        tenant2.setName("Beta");
        tenant2.setStatus(TenantStatus.ACTIVE);

        User admin1 = new User();
        admin1.setId(UUID.randomUUID());
        admin1.setTenantId(t1);
        admin1.setUsername("acme-admin");
        admin1.setRole(UserRole.TENANT_ADMIN);
        admin1.setStatus(UserStatus.ACTIVE);
        User admin1b = new User();
        admin1b.setId(UUID.randomUUID());
        admin1b.setTenantId(t1);
        admin1b.setUsername("acme-admin2");
        admin1b.setRole(UserRole.TENANT_ADMIN);
        admin1b.setStatus(UserStatus.ACTIVE);
        User admin2 = new User();
        admin2.setId(UUID.randomUUID());
        admin2.setTenantId(t2);
        admin2.setUsername("beta-admin");
        admin2.setRole(UserRole.TENANT_ADMIN);
        admin2.setStatus(UserStatus.ACTIVE);

        when(tenantRepository.findAllByStatusNot(eq(TenantStatus.DELETED), any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(tenant1, tenant2)));
        when(userRepository.findAllByTenantIdInAndRoleAndStatus(
            any(), eq(UserRole.TENANT_ADMIN), eq(UserStatus.ACTIVE)))
            .thenReturn(java.util.List.of(admin1, admin1b, admin2));

        var page = tenantService.listTenants(
            org.springframework.data.domain.PageRequest.of(0, 20), false);

        TenantDto dtoT1 = page.getContent().stream()
            .filter(t -> t.id().equals(t1)).findFirst().orElseThrow();
        TenantDto dtoT2 = page.getContent().stream()
            .filter(t -> t.id().equals(t2)).findFirst().orElseThrow();
        assertThat(dtoT1.admins()).extracting(TenantAdminDto::username)
            .containsExactlyInAnyOrder("acme-admin", "acme-admin2");
        assertThat(dtoT2.admins()).extracting(TenantAdminDto::username)
            .containsExactly("beta-admin");
    }

    @Test
    void purge_cascadesThroughKeyChildTables() {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName("Acme");
        tenant.setStatus(TenantStatus.DELETED);
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        tenantService.purge(id, "Acme");

        // Spot-check the chain: a few representative tables + the final tenants row.
        verify(jdbcTemplate).update(contains("FROM messages"), any(Object[].class));
        verify(jdbcTemplate).update(contains("FROM conversations"), any(Object[].class));
        verify(jdbcTemplate).update(contains("FROM customers"), any(Object[].class));
        verify(jdbcTemplate).update(contains("FROM users"), any(Object[].class));
        verify(jdbcTemplate).update(contains("FROM vector_store"), any(Object[].class));
        verify(jdbcTemplate).update(contains("FROM tenants"), any(Object[].class));
    }
}
