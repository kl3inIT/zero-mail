package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.persistence.AssistantSettingsEntity;
import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read / write the per-tenant assistant writing profile that drives both the chat assistant and the
 * inbox composer's "Generate" button.
 *
 * <p>The chat orchestrator already injects {@code personal_instructions} and {@code writing_style}
 * into the system prompt via {@link
 * com.zeromail.core.chat.sanitize.XmlFencedPersonalizationRenderer}, fenced inside XML tags and
 * sanitized against prompt injection. This service is the user-facing read/write surface so the
 * settings UI can drive what gets rendered.
 *
 * <p>Bean Validation at the controller layer caps lengths; this service trims and persists
 * verbatim. {@link AssistantSettingsEntity#applyProfile} updates all three fields atomically so a
 * single {@code PUT} call lands as one row revision.
 */
@Service
public class AssistantSettingsService {

    public static final int MAX_PERSONAL_INSTRUCTIONS_LENGTH = 2_000;
    public static final int MAX_WRITING_STYLE_LENGTH = 2_000;
    public static final int MAX_OUTPUT_LANGUAGE_LENGTH = 8;

    private final AssistantSettingsJpaRepository assistantSettingsRepository;

    public AssistantSettingsService(AssistantSettingsJpaRepository assistantSettingsRepository) {
        this.assistantSettingsRepository =
                Objects.requireNonNull(
                        assistantSettingsRepository,
                        "assistantSettingsRepository must not be null");
    }

    @Transactional(readOnly = true)
    public AssistantWritingProfile read(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return assistantSettingsRepository
                .findByTenantId(tenantId)
                .map(AssistantWritingProfile::fromEntity)
                .orElseGet(AssistantWritingProfile::empty);
    }

    @Transactional
    public AssistantWritingProfile update(UUID tenantId, AssistantWritingProfile incomingProfile) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(incomingProfile, "incomingProfile must not be null");
        String normalizedPersonalInstructions =
                normalize(
                        incomingProfile.personalInstructions(),
                        MAX_PERSONAL_INSTRUCTIONS_LENGTH,
                        "personalInstructions");
        String normalizedWritingStyle =
                normalize(incomingProfile.writingStyle(), MAX_WRITING_STYLE_LENGTH, "writingStyle");
        String normalizedAiOutputLanguage =
                normalize(
                        incomingProfile.aiOutputLanguage(),
                        MAX_OUTPUT_LANGUAGE_LENGTH,
                        "aiOutputLanguage");

        AssistantSettingsEntity settings =
                assistantSettingsRepository
                        .findByTenantId(tenantId)
                        .orElseGet(() -> AssistantSettingsEntity.defaults(tenantId));
        settings.applyProfile(
                normalizedPersonalInstructions, normalizedWritingStyle, normalizedAiOutputLanguage);
        AssistantSettingsEntity persisted = assistantSettingsRepository.save(settings);
        return AssistantWritingProfile.fromEntity(persisted);
    }

    private static String normalize(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.strip();
        if (trimmedValue.isEmpty()) {
            return null;
        }
        if (trimmedValue.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must be at most " + maxLength + " characters");
        }
        return trimmedValue;
    }

    public record AssistantWritingProfile(
            String personalInstructions, String writingStyle, String aiOutputLanguage) {

        public static AssistantWritingProfile empty() {
            return new AssistantWritingProfile(null, null, null);
        }

        public static AssistantWritingProfile fromEntity(AssistantSettingsEntity entity) {
            return new AssistantWritingProfile(
                    entity.getPersonalInstructions(),
                    entity.getWritingStyle(),
                    entity.getAiOutputLanguage());
        }
    }
}
