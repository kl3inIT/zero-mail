package com.zeromail.core.notification.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.notification.domain.ChannelType;
import com.zeromail.core.notification.usecases.NotificationPreferenceService;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationPreferencePersistenceTest extends PostgresContainerTest {

    @Autowired NotificationPreferenceService notificationPreferenceService;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void insertDefaults_roundTripsThroughHibernateWithUppercaseChannelStorage() {
        UUID tenantId = seedTenant();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                notificationPreferenceService.insertDefaults(
                                        tenantId, ChannelType.EMAIL, true, 20));

        NotificationPreferenceEntity notificationPreference =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        notificationPreferenceService
                                                .findByTenantAndChannel(tenantId, ChannelType.EMAIL)
                                                .orElseThrow());

        assertThat(notificationPreference.getTenantId()).isEqualTo(tenantId);
        assertThat(notificationPreference.getChannel()).isEqualTo(ChannelType.EMAIL);
        assertThat(notificationPreference.isDigestEnabled()).isTrue();
        assertThat(notificationPreference.getDigestSendHourLocal()).isEqualTo(20);

        String storedChannel =
                jdbcTemplate.queryForObject(
                        """
                        SELECT channel
                        FROM notification_preference
                        WHERE tenant_id = ?
                        """,
                        String.class,
                        tenantId);
        assertThat(storedChannel).isEqualTo("EMAIL");
    }

    @Test
    void insertDefaults_secondRowForSameTenantAndChannelFailsLoudly() {
        UUID tenantId = seedTenant();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                notificationPreferenceService.insertDefaults(
                                        tenantId, ChannelType.EMAIL, true, 20));

        assertThatThrownBy(
                        () ->
                                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                        .run(
                                                () ->
                                                        notificationPreferenceService
                                                                .insertDefaults(
                                                                        tenantId,
                                                                        ChannelType.EMAIL,
                                                                        false,
                                                                        9)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updatePreference_changesDigestEnabledAndSendHour() {
        UUID tenantId = seedTenant();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            notificationPreferenceService.insertDefaults(
                                    tenantId, ChannelType.EMAIL, true, 20);
                            notificationPreferenceService.updatePreference(
                                    tenantId, ChannelType.EMAIL, false, 8);
                        });

        NotificationPreferenceEntity notificationPreference =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        notificationPreferenceService
                                                .findByTenantAndChannel(tenantId, ChannelType.EMAIL)
                                                .orElseThrow());
        assertThat(notificationPreference.isDigestEnabled()).isFalse();
        assertThat(notificationPreference.getDigestSendHourLocal()).isEqualTo(8);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "tenant-" + tenantId);
        return tenantId;
    }
}
