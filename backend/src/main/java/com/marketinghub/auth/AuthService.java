package com.marketinghub.auth;

import com.marketinghub.auth.dto.AuthResponse;
import com.marketinghub.auth.dto.LoginRequest;
import com.marketinghub.auth.dto.RefreshRequest;
import com.marketinghub.auth.dto.UserDto;
import com.marketinghub.tenant.Tenant;
import com.marketinghub.tenant.TenantRepository;
import com.marketinghub.tenant.TenantStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantRepository tenantRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        TenantRepository tenantRepository,
        JwtService jwtService,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tenantRepository = tenantRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("Account is disabled");
        }
        // Tenant-level gate: SUSPENDED tenants can't log in, DELETED tenants definitely can't.
        // Platform admins (tenantId == null) skip this check.
        if (user.getTenantId() != null) {
            Tenant tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
            if (tenant == null || tenant.getStatus() != TenantStatus.ACTIVE) {
                throw new BadCredentialsException("Account is disabled");
            }
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        user.setLastLoginAt(Instant.now());
        return issueTokenPair(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not recognized"));
        if (stored.getRevokedAt() != null) {
            throw new InvalidRefreshTokenException("Refresh token already used");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }
        User user = userRepository.findById(stored.getUserId())
            .orElseThrow(() -> new InvalidRefreshTokenException("User no longer exists"));
        stored.setRevokedAt(Instant.now());
        return issueTokenPair(user);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String hash = sha256(request.refreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(Instant.now());
            }
        });
    }

    private AuthResponse issueTokenPair(User user) {
        String accessToken = jwtService.issueAccessToken(
            user.getId(), user.getEmail(), user.getTenantId(), user.getRole());
        String refreshTokenPlain = jwtService.issueRefreshToken();
        RefreshToken refresh = new RefreshToken();
        refresh.setUserId(user.getId());
        refresh.setTokenHash(sha256(refreshTokenPlain));
        refresh.setExpiresAt(jwtService.refreshTokenExpiry());
        refreshTokenRepository.save(refresh);
        return new AuthResponse(accessToken, refreshTokenPlain, toDto(user));
    }

    /** Non-static so we can resolve the tenant name. Platform admins get tenantName=null. */
    UserDto toDto(User user) {
        String tenantName = null;
        if (user.getTenantId() != null) {
            tenantName = tenantRepository.findById(user.getTenantId())
                .map(Tenant::getName)
                .orElse(null);
        }
        return new UserDto(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getTenantId(),
            tenantName,
            user.getRole()
        );
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
