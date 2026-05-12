package com.zeromail.core.gmail.persistence.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class NonceUniquenessTest {

    @Test
    void ten_thousand_unique_nonces() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        var cipher = new RefreshTokenCipher(Map.of(1, new SecretKeySpec(k, "AES")), 1);
        var nonces = new HashSet<String>();
        for (int i = 0; i < 10_000; i++) {
            byte[] env = cipher.encrypt("x".getBytes(), "t");
            byte[] nonce = new byte[12];
            ByteBuffer.wrap(env).position(4).get(nonce);
            nonces.add(HexFormat.of().formatHex(nonce));
        }
        assertThat(nonces).hasSize(10_000);
    }
}
