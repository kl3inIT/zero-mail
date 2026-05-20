package com.zeromail.core.admin.mkey.projection;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import java.time.Instant;

public record MasterKeyMaskedRow(
        LlmProvider provider,
        String maskedKey,
        KeyFormat keyFormat,
        Short kekVersion,
        long providerSecretVersion,
        Instant lastRotatedAt,
        long dependentsCount,
        boolean rotationRecommended,
        String baseUrl,
        boolean featureDefaultProviderChat,
        boolean featureDefaultProviderTriage,
        boolean featureDefaultProviderDraft) {}
