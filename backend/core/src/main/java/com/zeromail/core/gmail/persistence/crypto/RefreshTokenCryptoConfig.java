package com.zeromail.core.gmail.persistence.crypto;

import com.zeromail.core.crypto.config.CryptoProperties;
import com.zeromail.core.shared.crypto.PlatformSecretCipher;
import java.util.Base64;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RefreshTokenCryptoConfig {

    @Bean
    RefreshTokenCipher refreshTokenCipher(CryptoProperties cryptoProperties) {
        byte[] keyBytes = Base64.getDecoder().decode(cryptoProperties.refreshTokenKeyBase64());
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "AES-256 key must be exactly 32 bytes, got " + keyBytes.length);
        }
        return new RefreshTokenCipher(Map.of(1, new SecretKeySpec(keyBytes, "AES")), 1);
    }

    @Bean
    PlatformSecretCipher platformSecretCipher(CryptoProperties cryptoProperties) {
        byte[] keyBytes = Base64.getDecoder().decode(cryptoProperties.refreshTokenKeyBase64());
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "AES-256 key must be exactly 32 bytes, got " + keyBytes.length);
        }
        return new PlatformSecretCipher(Map.of(1, new SecretKeySpec(keyBytes, "AES")), 1);
    }
}
