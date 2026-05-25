package com.zeromail.core.admin.mkey.projection;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import java.time.Instant;

public record MasterKeyMaskedRow(
        LlmProvider provider,
        String displayName,
        String providerKind,
        String compatibleType,
        String defaultBaseUrl,
        String maskedKey,
        KeyFormat keyFormat,
        Short kekVersion,
        long providerSecretVersion,
        Instant lastRotatedAt,
        long dependentsCount,
        long activeKeyCount,
        boolean rotationRecommended,
        String baseUrl,
        boolean featureDefaultProviderChat,
        boolean featureDefaultProviderTriage,
        boolean featureDefaultProviderDraft) {

    /**
     * Sentinel value for {@link #dependentsCount} used by the list-page projection. The list page
     * does not compute per-row dependent counts (it would be one extra query per provider × tenant
     * for a number the list view never renders); the detail page owns that lookup. Use this
     * constant instead of a bare {@code 0L} so the intent ("not computed here") is obvious.
     */
    public static final long NO_DEPENDENTS = 0L;
}
