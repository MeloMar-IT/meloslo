package com.example.meloslo.util;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Component
public class EncryptionUtils {

    private static final Logger log = LoggerFactory.getLogger(EncryptionUtils.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final String AES = "AES";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final ThreadLocal<Cipher> ENCRYPT_CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    private static final ThreadLocal<Cipher> DECRYPT_CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    private record Keys(SecretKeySpec current, List<SecretKeySpec> legacy) {}

    /** Per-JVM random key, used only when no key is required and none is configured. */
    private static final Keys EPHEMERAL = new Keys(ephemeralKey(), List.of());
    private static volatile Keys keys = EPHEMERAL;

    @Value("${app.encryption.key:}")
    private String key;

    @Value("${app.encryption.legacy-keys:}")
    private String legacyKeys;

    @Value("${app.encryption.require-key:false}")
    private boolean requireKeyProp;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    private static SecretKeySpec ephemeralKey() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return new SecretKeySpec(b, AES);
    }

    @PostConstruct
    public void init() {
        boolean require = requireKeyProp || !datasourceUrl.startsWith("jdbc:h2:mem:");
        configure(key, legacyKeys, require);
    }

    /** Checks every key first, then swaps the state in one step. Messages never contain a key. */
    public static void configure(String key, String legacyKeysCsv, boolean requireKey) {
        List<SecretKeySpec> legacy = new ArrayList<>();
        if (legacyKeysCsv != null && !legacyKeysCsv.isBlank()) {
            String[] parts = legacyKeysCsv.split(",");
            for (int i = 0; i < parts.length; i++) {
                if (!parts[i].isBlank()) {
                    legacy.add(toKeySpec(parts[i].trim(), "APP_ENCRYPTION_LEGACY_KEYS[" + i + "]"));
                }
            }
        }
        if (key == null || key.isBlank()) {
            if (requireKey) {
                throw new IllegalStateException("APP_ENCRYPTION_KEY must be set (16, 24 or 32 bytes)");
            }
            log.warn("APP_ENCRYPTION_KEY is not set; using a random key for this JVM only. Encrypted data will not survive a restart.");
            keys = new Keys(EPHEMERAL.current(), List.copyOf(legacy));
            return;
        }
        keys = new Keys(toKeySpec(key, "APP_ENCRYPTION_KEY"), List.copyOf(legacy));
    }

    private static SecretKeySpec toKeySpec(String value, String label) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
            throw new IllegalStateException(label + " must be 16, 24 or 32 bytes long");
        }
        return new SecretKeySpec(bytes, AES);
    }

    public static String encrypt(String strToEncrypt) {
        if (strToEncrypt == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            RANDOM.nextBytes(iv);

            final Cipher cipher = ENCRYPT_CIPHER.get();
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keys.current(), parameterSpec);

            byte[] cipherText = cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Error while encrypting: " + e.toString());
        }
    }

    /** Returns the input unchanged when it cannot be decrypted (plaintext or unknown key). */
    public static String decrypt(String strToDecrypt) {
        return tryDecrypt(strToDecrypt).orElse(strToDecrypt);
    }

    /**
     * Empty for null, and for ciphertext that no configured key can decrypt.
     * Plaintext (not Base64, or too short to be ciphertext) comes back as is.
     */
    public static Optional<String> tryDecrypt(String value) {
        if (value == null) return Optional.empty();
        byte[] decode;
        try {
            decode = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return Optional.of(value);
        }
        if (decode.length < IV_LENGTH_BYTE + 16) return Optional.of(value);
        Keys k = keys;
        List<SecretKeySpec> candidates = new ArrayList<>();
        candidates.add(k.current());
        candidates.addAll(k.legacy());
        for (SecretKeySpec candidate : candidates) {
            try {
                Cipher cipher = DECRYPT_CIPHER.get();
                cipher.init(Cipher.DECRYPT_MODE, candidate, new GCMParameterSpec(TAG_LENGTH_BIT, decode, 0, IV_LENGTH_BYTE));
                byte[] plain = cipher.doFinal(decode, IV_LENGTH_BYTE, decode.length - IV_LENGTH_BYTE);
                return Optional.of(new String(plain, StandardCharsets.UTF_8));
            } catch (GeneralSecurityException e) {
                // try the next key
            }
        }
        return Optional.empty();
    }
}
