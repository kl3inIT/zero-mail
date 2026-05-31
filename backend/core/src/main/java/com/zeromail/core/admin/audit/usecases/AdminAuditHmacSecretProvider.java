package com.zeromail.core.admin.audit.usecases;

import com.zeromail.core.admin.config.AdminProperties;
import java.util.Base64;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Extracts and validates the HMAC chain key from configuration. Previously duplicated as {@code
 * hmacSecret()} private helpers in AdminAuditWriter and AdminAuditChainVerifier.
 */
@Component
public class AdminAuditHmacSecretProvider {

    private static final int MIN_SECRET_BYTES = 32;

    private final AdminProperties adminProperties;

    public AdminAuditHmacSecretProvider(AdminProperties adminProperties) {
        this.adminProperties =
                Objects.requireNonNull(adminProperties, "adminProperties must not be null");
    }

    public byte[] secret() {
        String hmacSecretBase64 = adminProperties.audit().hmacKekBase64();
        if (hmacSecretBase64 == null || hmacSecretBase64.isBlank()) {
            throw new IllegalStateException(
                    "zero-mail.admin.audit.hmac-kek-base64 must be configured");
        }
        byte[] hmacSecret = Base64.getDecoder().decode(hmacSecretBase64);
        if (hmacSecret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "zero-mail.admin.audit.hmac-kek-base64 must decode to at least "
                            + MIN_SECRET_BYTES
                            + " bytes");
        }
        return hmacSecret;
    }
}
