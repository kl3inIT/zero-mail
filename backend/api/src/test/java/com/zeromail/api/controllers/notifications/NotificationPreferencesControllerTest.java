package com.zeromail.api.controllers.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.notification.domain.ChannelType;
import com.zeromail.core.notification.persistence.NotificationPreferenceEntity;
import com.zeromail.core.notification.persistence.NotificationPreferenceRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class NotificationPreferencesControllerTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired NotificationPreferenceRepository notificationPreferenceRepository;

    @Test
    void get_without_authentication_returns_401() {
        ResponseEntity<String> response = getWithoutAuthentication("/api/me/notifications");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void get_returns_email_notification_preference_for_current_tenant() {
        Seed seed = seedUserWithPreference("notifications-get", true, 20);

        ResponseEntity<String> response = get("/api/me/notifications", seed);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .contains("\"channel\":\"EMAIL\"")
                .contains("\"digestEnabled\":true")
                .contains("\"digestSendHourLocal\":20")
                .contains("\"timeZone\":\"Asia/Ho_Chi_Minh\"");
    }

    @Test
    void patch_rejects_out_of_range_send_hour() {
        Seed seed = seedUserWithPreference("notifications-invalid", true, 20);

        ResponseEntity<String> response =
                patch(
                        "/api/me/notifications",
                        seed,
                        "{\"digestEnabled\":true,\"digestSendHourLocal\":24}");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void patch_updates_current_tenant_without_touching_other_tenants() {
        Seed tenantA = seedUserWithPreference("notifications-tenant-a", true, 20);
        Seed tenantB = seedUserWithPreference("notifications-tenant-b", true, 6);

        ResponseEntity<String> response =
                patch(
                        "/api/me/notifications",
                        tenantA,
                        "{\"digestEnabled\":false,\"digestSendHourLocal\":9}");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .contains("\"digestEnabled\":false")
                .contains("\"digestSendHourLocal\":9");
        assertThat(preferenceFor(tenantA.tenantId()).isDigestEnabled()).isFalse();
        assertThat(preferenceFor(tenantA.tenantId()).getDigestSendHourLocal()).isEqualTo(9);
        assertThat(preferenceFor(tenantB.tenantId()).isDigestEnabled()).isTrue();
        assertThat(preferenceFor(tenantB.tenantId()).getDigestSendHourLocal()).isEqualTo(6);
    }

    private ResponseEntity<String> get(String uri, Seed seed) {
        return RestClient.create("http://localhost:" + port)
                .get()
                .uri(uri)
                .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {})
                .toEntity(String.class);
    }

    private ResponseEntity<String> getWithoutAuthentication(String uri) {
        return RestClient.create("http://localhost:" + port)
                .get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {})
                .toEntity(String.class);
    }

    private ResponseEntity<String> patch(String uri, Seed seed, String json) {
        return RestClient.create("http://localhost:" + port)
                .patch()
                .uri(uri)
                .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {})
                .toEntity(String.class);
    }

    private Seed seedUserWithPreference(String label, boolean enabled, int sendHourLocal) {
        UUID tenantId = UUID.randomUUID();
        String uniqueLabel = label + "-" + UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, uniqueLabel));
        String googleSubject = "sub-" + uniqueLabel;
        String email = uniqueLabel + "@example.test";
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            userRepository.save(
                                    new UserEntity(
                                            UUID.randomUUID(), tenantId, googleSubject, email));
                            notificationPreferenceRepository.saveAndFlush(
                                    new NotificationPreferenceEntity(
                                            UUID.randomUUID(),
                                            tenantId,
                                            ChannelType.EMAIL,
                                            enabled,
                                            sendHourLocal));
                        });
        return new Seed(tenantId, googleSubject, email);
    }

    private NotificationPreferenceEntity preferenceFor(UUID tenantId) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(
                        () ->
                                notificationPreferenceRepository
                                        .findByTenantIdAndChannel(tenantId, ChannelType.EMAIL)
                                        .orElseThrow());
    }

    private record Seed(UUID tenantId, String googleSubject, String email) {}
}
