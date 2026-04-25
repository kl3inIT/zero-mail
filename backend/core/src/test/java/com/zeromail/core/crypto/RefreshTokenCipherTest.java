package com.zeromail.core.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class RefreshTokenCipherTest {

    private static RefreshTokenCipher c() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return new RefreshTokenCipher(Map.of(1, new SecretKeySpec(k, "AES")), 1);
    }

    @Test
    void round_trip() {
        var cipher = c();
        byte[] p = "refresh-token-value".getBytes();
        byte[] env = cipher.encrypt(p, "tenant-A");
        assertThat(cipher.decrypt(env, "tenant-A")).isEqualTo(p);
    }

    @Test
    void tenant_aad_mismatch_fails() {
        var cipher = c();
        byte[] env = cipher.encrypt("x".getBytes(), "tenant-A");
        assertThatThrownBy(() -> cipher.decrypt(env, "tenant-B"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknown_version_rejected() {
        var cipher = c();
        byte[] env = cipher.encrypt("x".getBytes(), "t");
        env[0] = 0; env[1] = 0; env[2] = 0; env[3] = 9; // mangle version
        assertThatThrownBy(() -> cipher.decrypt(env, "t"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown key version 9");
    }

    @Test
    void envelope_contains_version_and_nonce() {
        var cipher = c();
        byte[] env = cipher.encrypt(new byte[0], "t");
        assertThat(env.length).isGreaterThanOrEqualTo(4 + 12);
        assertThat(env[0]).isEqualTo((byte) 0);
        assertThat(env[3]).isEqualTo((byte) 1);
    }
}
