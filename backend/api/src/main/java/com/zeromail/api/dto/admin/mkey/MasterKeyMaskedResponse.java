package com.zeromail.api.dto.admin.mkey;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.mkey.projection.MasterKeyMaskedRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        requiredProperties = {
            "provider",
            "displayName",
            "providerKind",
            "providerSecretVersion",
            "dependentsCount",
            "activeKeyCount",
            "rotationRecommended",
            "featureDefaultProviderChat",
            "featureDefaultProviderTriage",
            "featureDefaultProviderDraft"
        })
public record MasterKeyMaskedResponse(
        String provider,
        String displayName,
        String providerKind,
        String compatibleType,
        String defaultBaseUrl,
        String maskedKey,
        String keyFormat,
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

    public static MasterKeyMaskedResponse from(MasterKeyMaskedRow masterKeyMaskedRow) {
        return new MasterKeyMaskedResponse(
                masterKeyMaskedRow.provider().id(),
                masterKeyMaskedRow.displayName(),
                masterKeyMaskedRow.providerKind(),
                masterKeyMaskedRow.compatibleType(),
                masterKeyMaskedRow.defaultBaseUrl(),
                masterKeyMaskedRow.maskedKey(),
                masterKeyMaskedRow.keyFormat() == null ? null : masterKeyMaskedRow.keyFormat().id(),
                masterKeyMaskedRow.kekVersion(),
                masterKeyMaskedRow.providerSecretVersion(),
                masterKeyMaskedRow.lastRotatedAt(),
                masterKeyMaskedRow.dependentsCount(),
                masterKeyMaskedRow.activeKeyCount(),
                masterKeyMaskedRow.rotationRecommended(),
                masterKeyMaskedRow.baseUrl(),
                masterKeyMaskedRow.featureDefaultProviderChat(),
                masterKeyMaskedRow.featureDefaultProviderTriage(),
                masterKeyMaskedRow.featureDefaultProviderDraft());
    }
}
