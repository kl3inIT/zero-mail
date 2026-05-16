package com.zeromail.api.controllers.notifications;

import com.zeromail.api.dto.notifications.NotificationPreferencesResponse;
import com.zeromail.api.dto.notifications.NotificationPreferencesUpdateRequest;
import com.zeromail.core.notification.domain.ChannelType;
import com.zeromail.core.notification.persistence.NotificationPreferenceEntity;
import com.zeromail.core.notification.usecases.NotificationPreferenceService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.usecases.TenantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "notifications")
@RequestMapping("/api/me/notifications")
public class NotificationPreferencesController {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationPreferencesController.class);

    private final NotificationPreferenceService notificationPreferenceService;
    private final TenantService tenantService;

    public NotificationPreferencesController(
            NotificationPreferenceService notificationPreferenceService,
            TenantService tenantService) {
        this.notificationPreferenceService = notificationPreferenceService;
        this.tenantService = tenantService;
    }

    @GetMapping
    public NotificationPreferencesResponse get() {
        UUID tenantId = TenantContext.currentTenantUuid();
        NotificationPreferenceEntity notificationPreference = findEmailPreference(tenantId);
        log.info("event=notification_preferences_read tenantId={}", tenantId);
        return NotificationPreferencesResponse.from(
                notificationPreference, tenantService.timeZoneFor(tenantId));
    }

    @PatchMapping
    public NotificationPreferencesResponse update(
            @Valid @RequestBody NotificationPreferencesUpdateRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        NotificationPreferenceEntity notificationPreference =
                notificationPreferenceService.updatePreference(
                        tenantId,
                        ChannelType.EMAIL,
                        request.digestEnabled(),
                        request.digestSendHourLocal());
        log.info("event=notification_preferences_updated tenantId={}", tenantId);
        return NotificationPreferencesResponse.from(
                notificationPreference, tenantService.timeZoneFor(tenantId));
    }

    private NotificationPreferenceEntity findEmailPreference(UUID tenantId) {
        return notificationPreferenceService
                .findByTenantAndChannel(tenantId, ChannelType.EMAIL)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Email notification preference missing for tenantId: "
                                                + tenantId));
    }
}
