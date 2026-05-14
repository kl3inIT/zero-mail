package com.zeromail.core.notification.usecases;

import com.zeromail.core.notification.domain.ChannelType;
import com.zeromail.core.notification.persistence.NotificationPreferenceEntity;
import com.zeromail.core.notification.persistence.NotificationPreferenceRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;

    public NotificationPreferenceService(
            NotificationPreferenceRepository notificationPreferenceRepository) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
    }

    @Transactional
    public NotificationPreferenceEntity insertDefaults(
            UUID tenantId, ChannelType channel, boolean enabled, int sendHourLocal) {
        NotificationPreferenceEntity notificationPreference =
                new NotificationPreferenceEntity(
                        UUID.randomUUID(), tenantId, channel, enabled, sendHourLocal);
        return notificationPreferenceRepository.saveAndFlush(notificationPreference);
    }

    @Transactional(readOnly = true)
    public Optional<NotificationPreferenceEntity> findByTenantAndChannel(
            UUID tenantId, ChannelType channel) {
        return notificationPreferenceRepository.findByTenantIdAndChannel(tenantId, channel);
    }

    @Transactional
    public NotificationPreferenceEntity updatePreference(
            UUID tenantId, ChannelType channel, boolean enabled, int sendHourLocal) {
        NotificationPreferenceEntity notificationPreference =
                notificationPreferenceRepository
                        .findByTenantIdAndChannel(tenantId, channel)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Notification preference not found for tenantId: "
                                                        + tenantId));
        notificationPreference.setDigestEnabled(enabled);
        notificationPreference.setDigestSendHourLocal(sendHourLocal);
        return notificationPreferenceRepository.save(notificationPreference);
    }

    @Transactional
    public void deleteForTenant(UUID tenantId) {
        notificationPreferenceRepository.deleteByTenantId(tenantId);
    }
}
