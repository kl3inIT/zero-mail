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
            "providerSecretVersion",
            "dependentsCount",
            "rotationRecommended",
            "featureDefaultProviderChat",
            "featureDefaultProviderTriage",
            "featureDefaultProviderDraft"
        })
public record MasterKeyMaskedResponse(
        String provider,
        String displayName,
        String maskedKey,
        String keyFormat,
        Short kekVersion,
        long providerSecretVersion,
        Instant lastRotatedAt,
        long dependentsCount,
        boolean rotationRecommended,
        String baseUrl,
        boolean featureDefaultProviderChat,
        boolean featureDefaultProviderTriage,
        boolean featureDefaultProviderDraft) {

    public static MasterKeyMaskedResponse from(MasterKeyMaskedRow masterKeyMaskedRow) {
        return new MasterKeyMaskedResponse(
                masterKeyMaskedRow.provider().id(),
                displayName(masterKeyMaskedRow.provider().id()),
                masterKeyMaskedRow.maskedKey(),
                masterKeyMaskedRow.keyFormat() == null ? null : masterKeyMaskedRow.keyFormat().id(),
                masterKeyMaskedRow.kekVersion(),
                masterKeyMaskedRow.providerSecretVersion(),
                masterKeyMaskedRow.lastRotatedAt(),
                masterKeyMaskedRow.dependentsCount(),
                masterKeyMaskedRow.rotationRecommended(),
                masterKeyMaskedRow.baseUrl(),
                masterKeyMaskedRow.featureDefaultProviderChat(),
                masterKeyMaskedRow.featureDefaultProviderTriage(),
                masterKeyMaskedRow.featureDefaultProviderDraft());
    }

    private static String displayName(String provider) {
        return switch (provider) {
            case "OPENAI" -> "OpenAI";
            case "ANTHROPIC" -> "Anthropic";
            case "GOOGLE" -> "Google";
            case "DEEPSEEK" -> "DeepSeek";
            case "OPENROUTER" -> "OpenRouter";
            case "ROUTER_9R" -> "9Router";
            default -> provider;
        };
    }
}
