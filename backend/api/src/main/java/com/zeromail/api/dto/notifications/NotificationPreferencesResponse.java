package com.zeromail.api.dto.notifications;

import com.zeromail.core.notification.persistence.NotificationPreferenceEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"channel", "digestEnabled", "digestSendHourLocal", "timeZone"})
public record NotificationPreferencesResponse(
        String channel, boolean digestEnabled, int digestSendHourLocal, String timeZone) {

    public static NotificationPreferencesResponse from(
            NotificationPreferenceEntity notificationPreference, String timeZone) {
        return new NotificationPreferencesResponse(
                notificationPreference.getChannel().id(),
                notificationPreference.isDigestEnabled(),
                notificationPreference.getDigestSendHourLocal(),
                timeZone);
    }
}
