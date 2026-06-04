package com.marketinghub.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final String secret;
    private final int accessTtlMinutes;
    private final int refreshTtlDays;
    private final ObjectMapper objectMapper;

    private SecretKey signingKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(
        @Value("${security.jwt.secret}") String secret,
        @Value("${security.jwt.access-token-ttl-minutes}") int accessTtlMinutes,
        @Value("${security.jwt.refresh-token-ttl-days}") int refreshTtlDays,
        ObjectMapper objectMapper
    ) {
        this.secret = secret;
        this.accessTtlMinutes = accessTtlMinutes;
        this.refreshTtlDays = refreshTtlDays;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "security.jwt.secret must be at least 32 characters (JWT_SECRET env var)"
            );
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * Issued via a small manual JWS builder so that a null tenantId for platform admins
     * is preserved as a literal JSON null in the payload. JJWT 0.12 strips null claims;
     * we keep using JJWT for parsing (parseAndValidate) since standard JWS verification
     * is unaffected by how the token was created.
     */
    public String issueAccessToken(UUID userId, String username, UUID tenantId, UserRole role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(accessTtlMinutes));

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userId.toString());
        claims.put("username", username);
        claims.put("role", role.name());
        claims.put("tenantId", tenantId == null ? null : tenantId.toString());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expiry.getEpochSecond());

        try {
            String headerEncoded = base64Url(objectMapper.writeValueAsBytes(header));
            String payloadEncoded = base64Url(objectMapper.writeValueAsBytes(claims));
            String signingInput = headerEncoded + "." + payloadEncoded;
            byte[] signature = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + base64Url(signature);
        } catch (JsonProcessingException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to issue access token", e);
        }
    }

    public String issueRefreshToken() {
        byte[] randomBytes = new byte[48];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(Duration.ofDays(refreshTtlDays));
    }

    public Claims parseAndValidate(String jwt) {
        Jws<Claims> jws = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(jwt);
        return jws.getPayload();
    }

    private byte[] hmacSha256(byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(signingKey);
        return mac.doFinal(data);
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
