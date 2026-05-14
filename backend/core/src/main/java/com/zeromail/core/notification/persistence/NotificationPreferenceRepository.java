package com.zeromail.core.notification.persistence;

import com.zeromail.core.notification.domain.ChannelType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreferenceEntity, UUID> {

    Optional<NotificationPreferenceEntity> findByTenantIdAndChannel(
            UUID tenantId, ChannelType channel);

    void deleteByTenantId(UUID tenantId);
}
