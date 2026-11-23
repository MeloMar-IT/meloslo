package com.example.meloslo.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionUtilsTest {

    private static final String KEY_A = "AAAAAAAAAAAAAAAA";
    private static final String KEY_B = "BBBBBBBBBBBBBBBB";
    private static final String KEY_C = "CCCCCCCCCCCCCCCC";

    @AfterEach
    void restoreDefault() {
        EncryptionUtils.configure("", "", false);
    }

    @Test
    void roundTripWorksForAllAesKeyLengths() {
        for (String key : new String[] {"1234567890123456", "123456789012345678901234", "12345678901234567890123456789012"}) {
            EncryptionUtils.configure(key, "", true);
            String enc = EncryptionUtils.encrypt("secret value");
            assertThat(enc).isNotEqualTo("secret value");
            assertThat(EncryptionUtils.decrypt(enc)).isEqualTo("secret value");
        }
    }

    @Test
    void badKeyLengthIsRejectedWithoutLeakingTheKey() {
        assertThatThrownBy(() -> EncryptionUtils.configure("short-key-15-by", "", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY")
                .hasMessageNotContaining("short-key-15-by");
    }

    @Test
    void badLegacyKeyIsRejectedWithoutLeakingTheKey() {
        assertThatThrownBy(() -> EncryptionUtils.configure(KEY_B, KEY_A + ",too-short", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_LEGACY_KEYS")
                .hasMessageNotContaining("too-short");
    }

    @Test
    void blankKeyWhenRequiredFailsAndKeepsEarlierKey() {
        EncryptionUtils.configure(KEY_A, "", true);
        String enc = EncryptionUtils.encrypt("keep me");

        assertThatThrownBy(() -> EncryptionUtils.configure("  ", "", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY");

        assertThat(EncryptionUtils.decrypt(enc)).isEqualTo("keep me");
    }

    @Test
    void blankKeyWhenNotRequiredUsesTheSameEphemeralKeyEachTime() {
        EncryptionUtils.configure("", "", false);
        String enc = EncryptionUtils.encrypt("value");
        EncryptionUtils.configure(null, "", false);

        assertThat(EncryptionUtils.decrypt(enc)).isEqualTo("value");
    }

    @Test
    void legacyKeyDecryptsOldDataButNewWritesUseTheCurrentKey() {
        EncryptionUtils.configure(KEY_A, "", true);
        String oldCipher = EncryptionUtils.encrypt("old data");

        EncryptionUtils.configure(KEY_B, KEY_A, true);
        assertThat(EncryptionUtils.decrypt(oldCipher)).isEqualTo("old data");
        String newCipher = EncryptionUtils.encrypt("new data");

        EncryptionUtils.configure(KEY_A, "", true);
        assertThat(EncryptionUtils.tryDecrypt(newCipher)).isEmpty();
        EncryptionUtils.configure(KEY_B, "", true);
        assertThat(EncryptionUtils.decrypt(newCipher)).isEqualTo("new data");
    }

    @Test
    void tryDecryptIsEmptyForUnknownKeyAndKeepsPlaintext() {
        EncryptionUtils.configure(KEY_C, "", true);
        String foreign = EncryptionUtils.encrypt("foreign");
        EncryptionUtils.configure(KEY_B, KEY_A, true);

        assertThat(EncryptionUtils.tryDecrypt(foreign)).isEmpty();
        assertThat(EncryptionUtils.decrypt(foreign)).isEqualTo(foreign);
        assertThat(EncryptionUtils.tryDecrypt("plain text")).contains("plain text");
        assertThat(EncryptionUtils.tryDecrypt("QUJD")).contains("QUJD");
        assertThat(EncryptionUtils.tryDecrypt(null)).isEmpty();
        assertThat(EncryptionUtils.decrypt(null)).isNull();
        assertThat(EncryptionUtils.encrypt(null)).isNull();
    }
}
