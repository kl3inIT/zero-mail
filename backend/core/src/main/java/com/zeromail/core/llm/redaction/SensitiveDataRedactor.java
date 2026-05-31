package com.zeromail.core.llm.redaction;

import com.zeromail.core.llm.gateway.sanitization.Sanitizer;
import com.zeromail.core.llm.usecases.SanitizationContext;
import com.zeromail.core.llm.usecases.SensitiveDataProtectionDecider;
import com.zeromail.core.tenant.TenantContext;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(35)
public class SensitiveDataRedactor implements Sanitizer {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d .()\\-]{7,}\\d)(?!\\d)");

    private final SensitiveDataProtectionDecider sensitiveDataProtectionDecider;

    @Autowired
    public SensitiveDataRedactor(
            ObjectProvider<SensitiveDataProtectionDecider> sensitiveDataProtectionDeciders) {
        Objects.requireNonNull(
                sensitiveDataProtectionDeciders,
                "sensitiveDataProtectionDeciders must not be null");
        this.sensitiveDataProtectionDecider =
                tenantId ->
                        sensitiveDataProtectionDeciders
                                .orderedStream()
                                .findFirst()
                                .map(decider -> decider.isSensitiveDataProtectionEnabled(tenantId))
                                .orElse(true);
    }

    SensitiveDataRedactor(SensitiveDataProtectionDecider sensitiveDataProtectionDecider) {
        this.sensitiveDataProtectionDecider =
                Objects.requireNonNull(
                        sensitiveDataProtectionDecider,
                        "sensitiveDataProtectionDecider must not be null");
    }

    @Override
    public SanitizationContext apply(SanitizationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        if (!sensitiveDataProtectionDecider.isSensitiveDataProtectionEnabled(tenantId)) {
            return context;
        }
        return context.withContent(redact(context.content()));
    }

    private static String redact(String content) {
        String emailRedacted =
                EMAIL_PATTERN
                        .matcher(Optional.ofNullable(content).orElse(""))
                        .replaceAll("[REDACTED_EMAIL]");
        return PHONE_PATTERN.matcher(emailRedacted).replaceAll("[REDACTED_PHONE]");
    }
}
