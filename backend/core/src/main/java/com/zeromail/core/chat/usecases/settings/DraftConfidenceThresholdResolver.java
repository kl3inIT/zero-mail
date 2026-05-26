package com.zeromail.core.chat.usecases.settings;

import com.zeromail.core.chat.persistence.AssistantSettingsEntity;
import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftConfidenceThresholdResolver {

    private static final double LOW_THRESHOLD = 0.50;
    private static final double MEDIUM_THRESHOLD = 0.70;
    private static final double HIGH_THRESHOLD = 0.85;

    private final AssistantSettingsJpaRepository assistantSettingsRepository;

    public DraftConfidenceThresholdResolver(
            AssistantSettingsJpaRepository assistantSettingsRepository) {
        this.assistantSettingsRepository =
                Objects.requireNonNull(
                        assistantSettingsRepository,
                        "assistantSettingsRepository must not be null");
    }

    @Transactional(readOnly = true)
    public double resolve(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        AssistantSettingsEntity.DraftConfidence draftConfidence =
                assistantSettingsRepository
                        .findByTenantId(tenantId)
                        .map(AssistantSettingsEntity::getDraftConfidence)
                        .orElse(AssistantSettingsEntity.DraftConfidence.MEDIUM);
        return thresholdFor(draftConfidence);
    }

    @Transactional(readOnly = true)
    public boolean meetsThreshold(UUID tenantId, double confidence) {
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        return confidence >= resolve(tenantId);
    }

    public static double thresholdFor(AssistantSettingsEntity.DraftConfidence draftConfidence) {
        return switch (Objects.requireNonNull(
                draftConfidence, "draftConfidence must not be null")) {
            case LOW -> LOW_THRESHOLD;
            case MEDIUM -> MEDIUM_THRESHOLD;
            case HIGH -> HIGH_THRESHOLD;
        };
    }
}
