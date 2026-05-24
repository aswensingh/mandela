package com.marketinghub.user;

import com.marketinghub.auth.AuthenticatedPrincipal;
import com.marketinghub.auth.EmailAlreadyUsedException;
import com.marketinghub.auth.RefreshTokenRepository;
import com.marketinghub.auth.User;
import com.marketinghub.auth.UserRepository;
import com.marketinghub.auth.UserRole;
import com.marketinghub.auth.UserStatus;
import com.marketinghub.tenant.TenantContext;
import com.marketinghub.user.dto.CreateUserRequest;
import com.marketinghub.user.dto.ResetPasswordResponse;
import com.marketinghub.user.dto.UpdateUserRequest;
import com.marketinghub.user.dto.UserSummaryDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private UserService userService;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, refreshTokenRepository, passwordEncoder);
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createUser_savesWithTenantScope() {
        when(userRepository.existsByTenantIdAndEmail(TENANT_A, "agent@a.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        UserSummaryDto dto = userService.createUser(new CreateUserRequest(
            "agent@a.com", "agentpass123", "Agent A", UserRole.AGENT));

        assertThat(dto.tenantId()).isEqualTo(TENANT_A);
        assertThat(dto.role()).isEqualTo(UserRole.AGENT);
        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getTenantId()).isEqualTo(TENANT_A);
        assertThat(passwordEncoder.matches("agentpass123", cap.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void createUser_rejectsPlatformAdmin() {
        assertThatThrownBy(() -> userService.createUser(new CreateUserRequest(
            "hack@a.com", "hackpass123", "Hack", UserRole.PLATFORM_ADMIN)))
            .isInstanceOf(InvalidRoleException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_rejectsDuplicateEmailInTenant() {
        when(userRepository.existsByTenantIdAndEmail(TENANT_A, "dup@a.com")).thenReturn(true);
        assertThatThrownBy(() -> userService.createUser(new CreateUserRequest(
            "dup@a.com", "duppass123", "Dup", UserRole.AGENT)))
            .isInstanceOf(EmailAlreadyUsedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_requiresTenantContext() {
        TenantContext.clear();
        assertThatThrownBy(() -> userService.createUser(new CreateUserRequest(
            "x@a.com", "xpass1234", "X", UserRole.AGENT)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listUsers_tenantAdminSeesAll() {
        AuthenticatedPrincipal admin = new AuthenticatedPrincipal(
            UUID.randomUUID(), "admin@a.com", TENANT_A, UserRole.TENANT_ADMIN);
        Pageable pageable = Pageable.unpaged();
        Page<User> page = new PageImpl<>(List.of(stub(UserRole.TENANT_ADMIN), stub(UserRole.AGENT)));
        when(userRepository.findAllByTenantId(TENANT_A, pageable)).thenReturn(page);

        Page<UserSummaryDto> result = userService.listUsers(admin, pageable);

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void listUsers_agentSeesOnlySelf() {
        UUID agentId = UUID.randomUUID();
        AuthenticatedPrincipal agent = new AuthenticatedPrincipal(agentId, "agent@a.com", TENANT_A, UserRole.AGENT);
        User self = stub(UserRole.AGENT);
        self.setId(agentId);
        when(userRepository.findByIdAndTenantId(agentId, TENANT_A)).thenReturn(Optional.of(self));

        Page<UserSummaryDto> result = userService.listUsers(agent, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(agentId);
        verify(userRepository, never()).findAllByTenantId(any(), any());
    }

    @Test
    void update_selfCanChangeFullName_butNotRole() {
        UUID agentId = UUID.randomUUID();
        AuthenticatedPrincipal agent = new AuthenticatedPrincipal(agentId, "agent@a.com", TENANT_A, UserRole.AGENT);
        User self = stub(UserRole.AGENT);
        self.setId(agentId);
        when(userRepository.findByIdAndTenantId(agentId, TENANT_A)).thenReturn(Optional.of(self));

        UserSummaryDto dto = userService.updateUser(agent, agentId,
            new UpdateUserRequest("New Name", null, null));
        assertThat(dto.fullName()).isEqualTo("New Name");

        assertThatThrownBy(() -> userService.updateUser(agent, agentId,
            new UpdateUserRequest(null, UserRole.TENANT_ADMIN, null)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_tenantAdminCannotEscalateToPlatformAdmin() {
        UUID targetId = UUID.randomUUID();
        AuthenticatedPrincipal admin = new AuthenticatedPrincipal(
            UUID.randomUUID(), "admin@a.com", TENANT_A, UserRole.TENANT_ADMIN);
        User target = stub(UserRole.AGENT);
        target.setId(targetId);
        when(userRepository.findByIdAndTenantId(targetId, TENANT_A)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.updateUser(admin, targetId,
            new UpdateUserRequest(null, UserRole.PLATFORM_ADMIN, null)))
            .isInstanceOf(InvalidRoleException.class);
    }

    @Test
    void update_blocksOtherTenantsUser() {
        UUID otherId = UUID.randomUUID();
        AuthenticatedPrincipal admin = new AuthenticatedPrincipal(
            UUID.randomUUID(), "admin@a.com", TENANT_A, UserRole.TENANT_ADMIN);
        when(userRepository.findByIdAndTenantId(otherId, TENANT_A)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.updateUser(admin, otherId,
            new UpdateUserRequest("x", null, null)))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void disable_setsStatus_andRevokesRefreshTokens() {
        UUID targetId = UUID.randomUUID();
        User target = stub(UserRole.AGENT);
        target.setId(targetId);
        when(userRepository.findByIdAndTenantId(targetId, TENANT_A)).thenReturn(Optional.of(target));

        UserSummaryDto dto = userService.disableUser(targetId);

        assertThat(dto.status()).isEqualTo(UserStatus.DISABLED);
        verify(refreshTokenRepository).revokeAllActiveForUser(eq(targetId), any());
    }

    @Test
    void disable_otherTenantsUser_yieldsNotFound() {
        UUID otherId = UUID.randomUUID();
        when(userRepository.findByIdAndTenantId(otherId, TENANT_A)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.disableUser(otherId))
            .isInstanceOf(UserNotFoundException.class);
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any(), any());
    }

    // ---------- reset password ----------

    @Test
    void resetPassword_tenantAdmin_resetsUserInOwnTenant_withCustomPassword() {
        UUID targetId = UUID.randomUUID();
        AuthenticatedPrincipal admin = new AuthenticatedPrincipal(
            UUID.randomUUID(), "admin@a.com", TENANT_A, UserRole.TENANT_ADMIN);
        User target = stub(UserRole.AGENT);
        target.setId(targetId);
        target.setTenantId(TENANT_A);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        ResetPasswordResponse response = userService.resetPassword(admin, targetId, "myNewPass123");

        assertThat(response.newPassword()).isEqualTo("myNewPass123");
        assertThat(response.generated()).isFalse();
        assertThat(passwordEncoder.matches("myNewPass123", target.getPasswordHash())).isTrue();
        verify(refreshTokenRepository).revokeAllActiveForUser(eq(targetId), any());
    }

    @Test
    void resetPassword_generatesPassword_whenNoneProvided() {
        UUID targetId = UUID.randomUUID();
        AuthenticatedPrincipal admin = new AuthenticatedPrincipal(
            UUID.randomUUID(), "admin@a.com", TENANT_A, UserRole.TENANT_ADMIN);
        User target = stub(UserRole.AGENT);
        target.setId(targetId);
        target.setTenantId(TENANT_A);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        ResetPasswordResponse response = userService.resetPassword(admin, targetId, null);

        assertThat(response.generated()).isTrue();
        assertThat(response.newPassword()).hasSize(16);
        assertThat(passwordEncoder.matches(response.newPassword(), target.getPasswordHash())).isTrue();
    }

    @Test
    void resetPassword_blankPasswordAlsoGenerates() {
        UUID targetId = UUID.randomUUID();
        AuthenticatedPrincipal admin = new AuthenticatedPrincipal(
            UUID.randomUUID(), "admin@a.com", TENANT_A, UserRole.TENANT_ADMIN);
        User target = stub(UserRole.AGENT);
        target.setId(targetId);
        target.setTenantId(TENANT_A);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        ResetPasswordResponse response = userService.resetPassword(admin, targetId, "   ");

        assertThat(response.generated()).isTrue();
        assertThat(response.newPassword()).hasSize(16);
    }

    @Test
    void resetPassword_rejectsShortPassword() {
        UUID targetId = UUID.randomUUID();
        AuthenticatedPrincipal admin = new AuthenticatedPrincipal(
            UUID.randomUUID(), "admin@a.com", TENANT_A, UserRole.TENANT_ADMIN);
        User target = stub(UserRole.AGENT);
        target.setId(targetId);
        target.setTenantId(TENANT_A);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.resetPassword(admin, targetId, "short"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least 8 characters");
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any(), any());
    }

    @Test
    void resetPassword_tenantAdmin_blockedFromOtherTenant() {
        UUID targetId = UUID.randomUUID();
        AuthenticatedPrincipal admin = new AuthenticatedPrincipal(
            UUID.randomUUID(), "admin@a.com", TENANT_A, UserRole.TENANT_ADMIN);
        User target = stub(UserRole.AGENT);
        target.setId(targetId);
        target.setTenantId(TENANT_B);  // <- different tenant
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.resetPassword(admin, targetId, "newPassword123"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("outside your tenant");
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any(), any());
    }

    @Test
    void resetPassword_platformAdmin_canResetAnyone() {
        UUID targetId = UUID.randomUUID();
        AuthenticatedPrincipal platformAdmin = new AuthenticatedPrincipal(
            UUID.randomUUID(), "root@platform", null, UserRole.PLATFORM_ADMIN);
        User target = stub(UserRole.TENANT_ADMIN);
        target.setId(targetId);
        target.setTenantId(TENANT_B);  // any tenant
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        ResetPasswordResponse response = userService.resetPassword(platformAdmin, targetId, null);

        assertThat(response.generated()).isTrue();
        assertThat(passwordEncoder.matches(response.newPassword(), target.getPasswordHash())).isTrue();
    }

    @Test
    void resetPassword_agent_isRejected() {
        UUID targetId = UUID.randomUUID();
        AuthenticatedPrincipal agent = new AuthenticatedPrincipal(
            UUID.randomUUID(), "agent@a.com", TENANT_A, UserRole.AGENT);
        User target = stub(UserRole.AGENT);
        target.setId(targetId);
        target.setTenantId(TENANT_A);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.resetPassword(agent, targetId, "newPassword123"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Only admins");
    }

    private static User stub(UserRole role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(TENANT_A);
        u.setEmail("u@a.com");
        u.setFullName("U");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setPasswordHash("x");
        return u;
    }
}
