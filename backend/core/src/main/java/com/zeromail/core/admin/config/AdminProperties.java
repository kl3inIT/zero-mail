package com.zeromail.core.admin.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Admin/ops config bound to {@code zero-mail.admin.*}.
 *
 * <p>Extracted from the former {@code ZeroMailCoreProperties} god-object (quick task w9t). The
 * bound keys are unchanged; only the Java owner moved. {@code @Validated} is preserved so the
 * {@code @Min(1)} / {@code @NotNull} spend constraints fail loud at bind time (R-8F-H6/H9). The
 * masked {@code toString()} keeps the audit HMAC KEK out of accidental bean logs.
 */
@ConfigurationProperties(prefix = "zero-mail.admin")
@Validated
public record AdminProperties(
        List<String> bootstrapEmails,
        @Valid @DefaultValue AdminAuditProperties audit,
        @Valid @DefaultValue AdminSpendProperties spend) {

    public AdminProperties {
        bootstrapEmails = bootstrapEmails == null ? List.of() : List.copyOf(bootstrapEmails);
    }

    public record AdminAuditProperties(@DefaultValue("") String hmacKekBase64) {

        @Override
        public @NonNull String toString() {
            return "AdminAuditProperties[hmacKekBase64=****]";
        }
    }

    /**
     * Spend-dashboard tunables per Phase 8F reviews-pass addenda.
     *
     * @param kAnonymityThreshold (R-8F-H6) minimum bucket size before exact per-tenant figures are
     *     exposed; smaller buckets collapse into a rollup row. {@code @Min(1)} is enforced by
     *     {@code @Validated} at bind time — no silent recovery.
     * @param rowLevelClassificationSince (R-8F-H9) boundary date for the credential_source
     *     row-level classification rollout; rows with {@code created_at} before this date are
     *     classified as UNKNOWN. The UI surfaces this date in the 90-day range picker label.
     */
    public record AdminSpendProperties(
            @Min(1) @DefaultValue("5") int kAnonymityThreshold,
            @NotNull @DefaultValue("2026-05-20") LocalDate rowLevelClassificationSince) {}

    @Override
    public @NonNull String toString() {
        return "AdminProperties[bootstrapEmails="
                + bootstrapEmails.size()
                + " entries, audit="
                + audit
                + ", spend="
                + spend
                + "]";
    }
}
