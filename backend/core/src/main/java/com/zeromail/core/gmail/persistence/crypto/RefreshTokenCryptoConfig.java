package com.zeromail.core.gmail.persistence.crypto;

import java.util.Base64;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zeromail.core.config.ZeroMailCoreProperties;

@Configuration
public class RefreshTokenCryptoConfig {

    @Bean
    RefreshTokenCipher refreshTokenCipher(ZeroMailCoreProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.refreshTokenKeyBase64());
        if (keyBytes.length != 32) {
            throw new IllegalStateException("AES-256 key must be exactly 32 bytes, got " + keyBytes.length);
        }
        return new RefreshTokenCipher(Map.of(1, new SecretKeySpec(keyBytes, "AES")), 1);
    }
}
