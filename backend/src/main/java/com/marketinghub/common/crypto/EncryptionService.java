package com.marketinghub.common.crypto;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for tenant secrets at rest.
 *
 * Wire format (single byte[] blob stored in DB):
 *   [iv (12 bytes)] [ciphertext + GCM tag]
 *
 * The 32-byte master key comes from env var ENCRYPTION_MASTER_KEY, base64-encoded.
 * Generate with: openssl rand -base64 32
 */
@Service
public class EncryptionService {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private static final String CIPHER = "AES/GCM/NoPadding";

    private final String masterKeyBase64;
    private SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public EncryptionService(@Value("${app.encryption.master-key}") String masterKeyBase64) {
        this.masterKeyBase64 = masterKeyBase64;
    }

    @PostConstruct
    public void init() {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            throw new IllegalStateException(
                "app.encryption.master-key is unset — set ENCRYPTION_MASTER_KEY (base64 of 32 random bytes)");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(masterKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ENCRYPTION_MASTER_KEY is not valid base64", e);
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException(
                "ENCRYPTION_MASTER_KEY must decode to exactly " + KEY_BYTES + " bytes (got "
                    + keyBytes.length + ")");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public byte[] encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer out = ByteBuffer.allocate(iv.length + ct.length);
            out.put(iv);
            out.put(ct);
            return out.array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(byte[] payload) {
        if (payload == null || payload.length <= IV_BYTES) {
            throw new IllegalArgumentException("Encrypted payload too short");
        }
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(payload, 0, iv, 0, IV_BYTES);
        byte[] ct = new byte[payload.length - IV_BYTES];
        System.arraycopy(payload, IV_BYTES, ct, 0, ct.length);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
