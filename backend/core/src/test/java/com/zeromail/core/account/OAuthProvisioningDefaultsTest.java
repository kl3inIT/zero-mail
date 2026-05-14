package com.zeromail.core.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.account.usecases.OAuthProvisioningService;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.notification.domain.ChannelType;
import com.zeromail.core.notification.persistence.NotificationPreferenceRepository;
import com.zeromail.core.notification.usecases.NotificationPreferenceService;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.usecases.TenantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class OAuthProvisioningDefaultsTest extends PostgresContainerTest {

    private static final String FAKE_REFRESH_TOKEN = "fake-defaults-refresh-token";
    private static final String GMAIL_SCOPES = "https://www.googleapis.com/auth/gmail.modify";

    @Autowired OAuthProvisioningService oauthProvisioningService;
    @Autowired UserRepository userRepository;
    @Autowired TenantService tenantService;
    @Autowired GmailConnectionService gmailConnectionService;
    @Autowired RefreshTokenCipher refreshTokenCipher;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired NotificationPreferenceRepository notificationPreferenceRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void firstLogin_insertsTenantTimeZoneAndDefaultEmailNotificationPreference() {
        String googleSubject = "google-subject-defaults-" + UUID.randomUUID();
        String email = "defaults-" + UUID.randomUUID() + "@example.test";

        OAuthProvisioningService.BundledProvisioningResult result =
                oauthProvisioningService.provisionBundledOAuth(
                        googleSubject, email, FAKE_REFRESH_TOKEN, GMAIL_SCOPES);

        assertThat(result.firstLogin()).isTrue();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT time_zone FROM tenants WHERE id = ?",
                                String.class,
                                result.tenantId()))
                .isEqualTo(TenantEntity.DEFAULT_TIME_ZONE);
        NotificationPreferenceSnapshot notificationPreference =
                jdbcTemplate.queryForObject(
                        """
                        SELECT channel, digest_enabled, digest_send_hour_local
                        FROM notification_preference
                        WHERE tenant_id = ?
                        """,
                        (resultSet, rowNumber) ->
                                new NotificationPreferenceSnapshot(
                                        resultSet.getString("channel"),
                                        resultSet.getBoolean("digest_enabled"),
                                        resultSet.getInt("digest_send_hour_local")),
                        result.tenantId());
        assertThat(notificationPreference)
                .isEqualTo(new NotificationPreferenceSnapshot("EMAIL", true, 20));
    }

    @Test
    void notificationPreferenceFailure_rollsBackUserTenantAndGmailConnectionRows() {
        String googleSubject = "google-subject-rollback-" + UUID.randomUUID();
        String email = "rollback-" + UUID.randomUUID() + "@example.test";
        NotificationPreferenceService failingNotificationPreferenceService =
                new NotificationPreferenceService(notificationPreferenceRepository) {
                    @Override
                    public com.zeromail.core.notification.persistence.NotificationPreferenceEntity
                            insertDefaults(
                                    UUID tenantId,
                                    ChannelType channel,
                                    boolean enabled,
                                    int sendHourLocal) {
                        throw new RuntimeException("synthetic notification preference failure");
                    }
                };
        OAuthProvisioningService failingProvisioningService =
                new OAuthProvisioningService(
                        userRepository,
                        tenantService,
                        gmailConnectionService,
                        failingNotificationPreferenceService,
                        refreshTokenCipher,
                        transactionManager);

        assertThatThrownBy(
                        () ->
                                failingProvisioningService.provisionBundledOAuth(
                                        googleSubject, email, FAKE_REFRESH_TOKEN, GMAIL_SCOPES))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("synthetic notification preference failure");

        assertThat(countRows("users", "google_subject", googleSubject)).isZero();
        assertThat(countRows("tenants", "display_name", email)).isZero();
        assertThat(countRows("gmail_connections", "google_email", email)).isZero();
    }

    private long countRows(String tableName, String columnName, String value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ?",
                Long.class,
                value);
    }

    private record NotificationPreferenceSnapshot(
            String channel, boolean digestEnabled, int digestSendHourLocal) {}
}
