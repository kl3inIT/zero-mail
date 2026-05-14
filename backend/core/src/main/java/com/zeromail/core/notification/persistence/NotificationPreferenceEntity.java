package com.zeromail.core.notification.persistence;

import com.zeromail.core.notification.domain.ChannelType;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
        name = "notification_preference",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_notification_preference_tenant_channel",
                        columnNames = {"tenant_id", "channel"}))
public class NotificationPreferenceEntity extends AbstractTenantOwnedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private ChannelType channel;

    @Column(name = "digest_enabled", nullable = false)
    private boolean digestEnabled;

    @Column(name = "digest_send_hour_local", nullable = false)
    private int digestSendHourLocal;

    protected NotificationPreferenceEntity() {}

    public NotificationPreferenceEntity(
            UUID id,
            UUID tenantId,
            ChannelType channel,
            boolean digestEnabled,
            int digestSendHourLocal) {
        super(id, tenantId);
        this.channel = channel;
        this.digestEnabled = digestEnabled;
        setDigestSendHourLocal(digestSendHourLocal);
    }

    public ChannelType getChannel() {
        return channel;
    }

    public boolean isDigestEnabled() {
        return digestEnabled;
    }

    public void setDigestEnabled(boolean digestEnabled) {
        this.digestEnabled = digestEnabled;
    }

    public int getDigestSendHourLocal() {
        return digestSendHourLocal;
    }

    public void setDigestSendHourLocal(int digestSendHourLocal) {
        if (digestSendHourLocal < 0 || digestSendHourLocal > 23) {
            throw new IllegalArgumentException("digestSendHourLocal must be between 0 and 23");
        }
        this.digestSendHourLocal = digestSendHourLocal;
    }
}
