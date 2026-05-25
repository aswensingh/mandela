package com.marketinghub.auth;

import com.marketinghub.auth.dto.AuthResponse;
import com.marketinghub.auth.dto.LoginRequest;
import com.marketinghub.tenant.Tenant;
import com.marketinghub.tenant.TenantRepository;
import com.marketinghub.tenant.TenantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // low cost for tests

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository, refreshTokenRepository, tenantRepository, jwtService, passwordEncoder);
    }

    private static Tenant activeTenant(UUID id) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setName("Test");
        t.setStatus(TenantStatus.ACTIVE);
        return t;
    }

    @Test
    void login_returnsTokens_andEmbedsRoleAndTenantInAccessToken() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("alice@example.com");
        user.setPasswordHash(passwordEncoder.encode("topsecret123"));
        user.setStatus(UserStatus.ACTIVE);
        user.setFullName("Alice");
        user.setRole(UserRole.TENANT_ADMIN);
        user.setTenantId(tenantId);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenant(tenantId)));
        when(jwtService.issueAccessToken(userId, "alice@example.com", tenantId, UserRole.TENANT_ADMIN))
            .thenReturn("ACCESS-TOK");
        when(jwtService.issueRefreshToken()).thenReturn("REFRESH-TOK");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(3600));

        AuthResponse response = authService.login(new LoginRequest("alice@example.com", "topsecret123"));

        assertThat(response.accessToken()).isEqualTo("ACCESS-TOK");
        assertThat(response.refreshToken()).isEqualTo("REFRESH-TOK");
        assertThat(response.user().email()).isEqualTo("alice@example.com");
        assertThat(response.user().tenantId()).isEqualTo(tenantId);
        assertThat(response.user().tenantName()).isEqualTo("Test"); // from activeTenant helper
        assertThat(response.user().role()).isEqualTo(UserRole.TENANT_ADMIN);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void login_platformAdmin_hasNullTenantInToken() {
        UUID userId = UUID.randomUUID();
        User admin = new User();
        admin.setId(userId);
        admin.setEmail("admin@marketinghub.local");
        admin.setPasswordHash(passwordEncoder.encode("topsecret123"));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setRole(UserRole.PLATFORM_ADMIN);
        admin.setTenantId(null);

        when(userRepository.findByEmail("admin@marketinghub.local")).thenReturn(Optional.of(admin));
        when(jwtService.issueAccessToken(userId, "admin@marketinghub.local", null, UserRole.PLATFORM_ADMIN))
            .thenReturn("ADMIN-ACCESS");
        when(jwtService.issueRefreshToken()).thenReturn("ADMIN-REFRESH");
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(3600));

        AuthResponse response = authService.login(
            new LoginRequest("admin@marketinghub.local", "topsecret123"));

        assertThat(response.user().tenantId()).isNull();
        assertThat(response.user().tenantName()).isNull();
        assertThat(response.user().role()).isEqualTo(UserRole.PLATFORM_ADMIN);
        verify(jwtService).issueAccessToken(userId, "admin@marketinghub.local", null, UserRole.PLATFORM_ADMIN);
    }

    @Test
    void login_throwsOnWrongPassword() {
        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("alice@example.com");
        user.setPasswordHash(passwordEncoder.encode("topsecret123"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.TENANT_ADMIN);
        user.setTenantId(tenantId);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenant(tenantId)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong-password")))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_throwsOnMissingUser() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever")))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_throwsWhenTenantSuspended() {
        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("alice@example.com");
        user.setPasswordHash(passwordEncoder.encode("topsecret123"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.TENANT_ADMIN);
        user.setTenantId(tenantId);

        Tenant suspended = activeTenant(tenantId);
        suspended.setStatus(TenantStatus.SUSPENDED);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "topsecret123")))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_throwsWhenTenantDeleted() {
        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("alice@example.com");
        user.setPasswordHash(passwordEncoder.encode("topsecret123"));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.TENANT_ADMIN);
        user.setTenantId(tenantId);

        Tenant deleted = activeTenant(tenantId);
        deleted.setStatus(TenantStatus.DELETED);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "topsecret123")))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void sha256_isStable() {
        String hash1 = AuthService.sha256("hello");
        String hash2 = AuthService.sha256("hello");
        assertThat(hash1).isEqualTo(hash2).hasSize(64);
    }
}
