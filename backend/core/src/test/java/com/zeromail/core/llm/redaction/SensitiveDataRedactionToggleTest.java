package com.zeromail.core.llm.redaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.zeromail.core.chat.persistence.AssistantSettingsEntity;
import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import com.zeromail.core.chat.usecases.settings.AssistantDraftSettingsService;
import com.zeromail.core.llm.usecases.SanitizationContext;
import com.zeromail.core.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SensitiveDataRedactionToggleTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000090204");
    private static final String SENSITIVE_CONTENT =
            "Email alice@example.com or call +1 415 555 0199.";

    @Test
    void sensitiveDataProtectionToggleControlsRedaction() throws Exception {
        assertThat(redactedContent(true))
                .doesNotContain("alice@example.com", "+1 415 555 0199")
                .contains("[REDACTED_EMAIL]", "[REDACTED_PHONE]");
        assertThat(redactedContent(false)).isEqualTo(SENSITIVE_CONTENT);
    }

    private static String redactedContent(boolean sensitiveDataProtectionEnabled) throws Exception {
        AssistantSettingsJpaRepository assistantSettingsRepository =
                mock(AssistantSettingsJpaRepository.class);
        AssistantSettingsEntity assistantSettings = AssistantSettingsEntity.defaults(TENANT_ID);
        assistantSettings.applyBehaviorSettings(
                true,
                AssistantSettingsEntity.DraftConfidence.MEDIUM,
                sensitiveDataProtectionEnabled);
        given(assistantSettingsRepository.findByTenantId(TENANT_ID))
                .willReturn(Optional.of(assistantSettings));
        SensitiveDataRedactor sensitiveDataRedactor =
                new SensitiveDataRedactor(
                        new AssistantDraftSettingsService(assistantSettingsRepository));

        return ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .call(
                        () ->
                                sensitiveDataRedactor
                                        .apply(SanitizationContext.initial(SENSITIVE_CONTENT))
                                        .content());
    }
}
