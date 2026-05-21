package com.zeromail.core.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PlatformSecretCipherTest {

    @Test
    void round_trips_with_matching_associated_data() {
        PlatformSecretCipher cipher = testCipher();
        byte[] plaintext = "platform-key-value".getBytes(StandardCharsets.UTF_8);

        byte[] envelope = cipher.encrypt(plaintext, "platform:master_key:OPENAI");

        assertThat(cipher.decrypt(envelope, "platform:master_key:OPENAI")).isEqualTo(plaintext);
    }

    @Test
    void rejects_row_swap_when_provider_associated_data_changes() {
        PlatformSecretCipher cipher = testCipher();
        byte[] plaintext = "platform-key-value".getBytes(StandardCharsets.UTF_8);
        byte[] openAiEnvelope = cipher.encrypt(plaintext, "platform:master_key:OPENAI");

        assertThatThrownBy(() -> cipher.decrypt(openAiEnvelope, "platform:master_key:ANTHROPIC"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static PlatformSecretCipher testCipher() {
        byte[] keyBytes = new byte[32];
        for (int byteIndex = 0; byteIndex < keyBytes.length; byteIndex++) {
            keyBytes[byteIndex] = (byte) (byteIndex + 1);
        }
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        return new PlatformSecretCipher(Map.of(7, secretKey), 7);
    }
}
