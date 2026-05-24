package com.marketinghub.common.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void roundTrip_recoversPlaintext() {
        EncryptionService svc = newService(VALID_KEY);
        byte[] ct = svc.encrypt("super-secret-meta-token");
        assertThat(svc.decrypt(ct)).isEqualTo("super-secret-meta-token");
    }

    @Test
    void ciphertext_isNotPlaintext() {
        EncryptionService svc = newService(VALID_KEY);
        String secret = "EAAJ1234567890abc";
        byte[] ct = svc.encrypt(secret);
        // The encrypted blob must NOT contain the plaintext bytes anywhere.
        assertThat(containsSubsequence(ct, secret.getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void twoEncryptionsOfSameInput_produceDifferentCiphertexts() {
        EncryptionService svc = newService(VALID_KEY);
        byte[] a = svc.encrypt("same-input");
        byte[] b = svc.encrypt("same-input");
        assertThat(Arrays.equals(a, b)).isFalse(); // random IV
    }

    @Test
    void initFailsOnMissingKey() {
        assertThatThrownBy(() -> newService(""))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("master-key");
    }

    @Test
    void initFailsOnWrongKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> newService(shortKey))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32 bytes");
    }

    @Test
    void initFailsOnNonBase64Key() {
        assertThatThrownBy(() -> newService("@@@not-base64@@@"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("base64");
    }

    @Test
    void decryptFailsOnTamperedCiphertext() {
        EncryptionService svc = newService(VALID_KEY);
        byte[] ct = svc.encrypt("hello");
        ct[ct.length - 1] ^= 0x01; // flip a bit in the GCM tag
        assertThatThrownBy(() -> svc.decrypt(ct))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Decryption failed");
    }

    private static EncryptionService newService(String key) {
        EncryptionService svc = new EncryptionService(key);
        svc.init();
        return svc;
    }

    private static boolean containsSubsequence(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) return false;
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
