package com.zeromail.worker.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.zeromail.core.chat.persistence.AssistantSettingsEntity;
import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import com.zeromail.core.chat.usecases.settings.DraftConfidenceThresholdResolver;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DraftConfidenceThresholdTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000090202");

    @ParameterizedTest
    @CsvSource({"LOW,0.50,0.49,0.50", "MEDIUM,0.70,0.69,0.70", "HIGH,0.85,0.84,0.85"})
    void draftWorkerMapsConfidenceEnumToThreshold(
            AssistantSettingsEntity.DraftConfidence draftConfidence,
            double expectedThreshold,
            double belowThreshold,
            double atThreshold) {
        AssistantSettingsJpaRepository assistantSettingsRepository =
                mock(AssistantSettingsJpaRepository.class);
        AssistantSettingsEntity assistantSettings = AssistantSettingsEntity.defaults(TENANT_ID);
        assistantSettings.applyBehaviorSettings(true, draftConfidence, true);
        given(assistantSettingsRepository.findByTenantId(TENANT_ID))
                .willReturn(Optional.of(assistantSettings));
        DraftConfidenceThresholdResolver thresholdResolver =
                new DraftConfidenceThresholdResolver(assistantSettingsRepository);

        assertThat(thresholdResolver.resolve(TENANT_ID)).isEqualTo(expectedThreshold);
        assertThat(thresholdResolver.meetsThreshold(TENANT_ID, belowThreshold)).isFalse();
        assertThat(thresholdResolver.meetsThreshold(TENANT_ID, atThreshold)).isTrue();
    }
}
