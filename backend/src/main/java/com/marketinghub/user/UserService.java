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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    // Crockford-ish alphabet — drops 0/O, 1/I/l, etc. so a copy-paste of the generated
    // password is unambiguous over the phone.
    private static final String GENERATED_ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int GENERATED_LENGTH = 16;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryDto> listUsers(AuthenticatedPrincipal caller, Pageable pageable) {
        UUID tenantId = requireTenant();
        if (caller.role() == UserRole.TENANT_ADMIN) {
            return userRepository.findAllByTenantId(tenantId, pageable).map(UserService::toDto);
        }
        // AGENT / VIEWER: only see themselves
        return userRepository.findByIdAndTenantId(caller.userId(), tenantId)
            .map(u -> (Page<UserSummaryDto>) new PageImpl<>(List.of(toDto(u))))
            .orElseGet(() -> new PageImpl<>(List.of()));
    }

    @Transactional
    public UserSummaryDto createUser(CreateUserRequest request) {
        UUID tenantId = requireTenant();
        if (request.role() == UserRole.PLATFORM_ADMIN) {
            throw new InvalidRoleException("Cannot create PLATFORM_ADMIN inside a tenant");
        }
        if (userRepository.existsByTenantIdAndEmail(tenantId, request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(request.role());
        user.setStatus(UserStatus.ACTIVE);
        user.setTenantId(tenantId);
        User saved = userRepository.save(user);
        userRepository.flush();
        return toDto(saved);
    }

    @Transactional
    public UserSummaryDto updateUser(AuthenticatedPrincipal caller, UUID userId, UpdateUserRequest request) {
        UUID tenantId = requireTenant();
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        boolean isSelf = user.getId().equals(caller.userId());
        boolean isTenantAdmin = caller.role() == UserRole.TENANT_ADMIN;

        if (!isTenantAdmin && !isSelf) {
            throw new AccessDeniedException("Cannot update another user");
        }

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName());
        }

        if (request.role() != null) {
            if (!isTenantAdmin) {
                throw new AccessDeniedException("Only TENANT_ADMIN can change role");
            }
            if (request.role() == UserRole.PLATFORM_ADMIN) {
                throw new InvalidRoleException("Cannot escalate to PLATFORM_ADMIN");
            }
            user.setRole(request.role());
        }

        if (request.status() != null) {
            if (!isTenantAdmin) {
                throw new AccessDeniedException("Only TENANT_ADMIN can change status");
            }
            user.setStatus(request.status());
            if (request.status() == UserStatus.DISABLED) {
                refreshTokenRepository.revokeAllActiveForUser(user.getId(), Instant.now());
            }
        }

        return toDto(user);
    }

    @Transactional
    public UserSummaryDto disableUser(UUID userId) {
        UUID tenantId = requireTenant();
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        user.setStatus(UserStatus.DISABLED);
        refreshTokenRepository.revokeAllActiveForUser(user.getId(), Instant.now());
        return toDto(user);
    }

    /**
     * Admin-assisted password reset. Either the caller passes a new password, or the server
     * generates one. Either way the plaintext is returned ONCE — the admin is expected to
     * convey it to the end user out-of-band (in person, on the phone, etc.) and the user
     * should change it on next login (no self-service flow for that yet).
     *
     * Authorization:
     * - PLATFORM_ADMIN may reset any user (any tenant, or another platform admin).
     * - TENANT_ADMIN may reset any user in their own tenant.
     * - AGENT / VIEWER may not reset (controller's @PreAuthorize blocks this, defence-in-depth here).
     *
     * Side effect: revokes all active refresh tokens for the user so existing sessions die
     * immediately. The user will be forced to log in again with the new password.
     */
    @Transactional
    public ResetPasswordResponse resetPassword(
        AuthenticatedPrincipal caller,
        UUID targetUserId,
        String requestedPassword
    ) {
        User target = userRepository.findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException(targetUserId));

        switch (caller.role()) {
            case PLATFORM_ADMIN -> { /* may reset anyone */ }
            case TENANT_ADMIN -> {
                if (target.getTenantId() == null
                    || !target.getTenantId().equals(caller.tenantId())) {
                    throw new AccessDeniedException("Cannot reset users outside your tenant");
                }
            }
            default -> throw new AccessDeniedException("Only admins can reset passwords");
        }

        boolean generated = (requestedPassword == null || requestedPassword.isBlank());
        String finalPassword = generated ? generatePassword() : requestedPassword;

        if (finalPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        target.setPasswordHash(passwordEncoder.encode(finalPassword));
        int revoked = refreshTokenRepository.revokeAllActiveForUser(target.getId(), Instant.now());

        log.info("Password reset for user {} (email={}) by {} (generated={}, revoked {} sessions)",
            target.getId(), target.getEmail(), caller.email(), generated, revoked);

        return new ResetPasswordResponse(target.getId(), target.getEmail(), finalPassword, generated);
    }

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            sb.append(GENERATED_ALPHABET.charAt(RANDOM.nextInt(GENERATED_ALPHABET.length())));
        }
        return sb.toString();
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context — platform admins cannot manage tenant users here");
        }
        return tenantId;
    }

    static UserSummaryDto toDto(User user) {
        return new UserSummaryDto(
            user.getId(),
            user.getTenantId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole(),
            user.getStatus(),
            user.getLastLoginAt(),
            user.getCreatedAt()
        );
    }
}
