package com.cernecommerce.adapter.out.security.totp;

import com.cernecommerce.core.ports.out.SecretCipherPort;
import com.cernecommerce.core.ports.out.twofa.TotpEncryptionPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Criptografia AES-256-GCM para o secret TOTP e os tokens de integração em repouso.
 *
 * Formato armazenado: Base64(iv || ciphertext || tag) — IV de 12 bytes + tag de 128 bits (16 bytes).
 * A chave deve ser 32 bytes (256 bits) encodados em Base64 URL-safe.
 * Configure via: totp.encryption.key=<base64-url-safe-32-bytes>
 */
@Component
public class AesEncryptionAdapter implements TotpEncryptionPort, SecretCipherPort {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesEncryptionAdapter(@Value("${totp.encryption.key}") String keyBase64) {
        byte[] keyBytes = Base64.getUrlDecoder().decode(keyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("totp.encryption.key must be 32 bytes (256-bit) encoded as Base64");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
            byte[] result = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, GCM_IV_LENGTH, ciphertext.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Secret encryption failed", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            byte[] data = Base64.getUrlDecoder().decode(ciphertext);
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH);
            byte[] encrypted = new byte[data.length - GCM_IV_LENGTH];
            System.arraycopy(data, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted));
        } catch (Exception e) {
            throw new IllegalStateException("Secret decryption failed", e);
        }
    }
}
