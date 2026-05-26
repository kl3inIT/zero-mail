package com.zeromail.worker.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.zeromail.core.chat.persistence.AssistantSettingsEntity;
import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import com.zeromail.core.chat.usecases.settings.AssistantDraftSettingsService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DraftAutoToggleIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000090201");

    @Test
    void draftWorkerWritesNoDraftsWhenAutoDraftRepliesIsOff() {
        AssistantSettingsJpaRepository assistantSettingsRepository =
                mock(AssistantSettingsJpaRepository.class);
        AssistantSettingsEntity assistantSettings = AssistantSettingsEntity.defaults(TENANT_ID);
        assistantSettings.applyBehaviorSettings(
                false, AssistantSettingsEntity.DraftConfidence.MEDIUM, true);
        given(assistantSettingsRepository.findByTenantId(TENANT_ID))
                .willReturn(Optional.of(assistantSettings));

        AssistantDraftSettingsService assistantDraftSettingsService =
                new AssistantDraftSettingsService(assistantSettingsRepository);

        assertThat(assistantDraftSettingsService.autoDraftRepliesEnabled(TENANT_ID)).isFalse();
    }
}
