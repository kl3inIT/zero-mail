package com.zeromail.core.calendar.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.calendar.domain.CalendarConnectionStatus;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.oauth.token.OAuthTokenStore;
import com.zeromail.core.oauth.token.OAuthTokenStore.RowDiscriminator;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cipher round-trip + cross-write isolation gate for the Phase 12 W1 Calendar entity. Pins three
 * invariants the W1 success-handler path depends on:
 *
 * <ol>
 *   <li>{@code OAuthTokenStore.encrypt(..., RowDiscriminator.CALENDAR_CONNECTION)} + JPA persist +
 *       JPA re-load + {@code OAuthTokenStore.decrypt(...)} round-trips the same plaintext.
 *   <li>(T-12-05 Pitfall 2) Writing a Calendar ciphertext to {@code
 *       calendar_connections.refresh_token_encrypted} leaves the Gmail row's {@code
 *       refresh_token_encrypted} column byte-identical — no cross-table leak via Hibernate flush
 *       order or column-name collision.
 *   <li>The {@code CalendarConnectionEntity} has NO {@code gmail_connection_id} column (already
 *       checked by {@code CalendarSchemaIsolationTest} from the DB side; this test re-asserts from
 *       the entity side via reflection so a future entity edit cannot quietly add the FK).
 * </ol>
 *
 * <p>Privacy: no {@code googleEmail} ever appears in test output (assertion messages and log lines
 * carry only opaque UUIDs).
 */
class CalendarConnectionCipherTest extends PostgresContainerTest {

    @Autowired CalendarConnectionRepository calendarConnectionRepository;
    @Autowired GmailConnectionRepository gmailConnectionRepository;
    @Autowired OAuthTokenStore oAuthTokenStore;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void calendar_round_trip_encrypts_and_decrypts_via_calendar_discriminator() {
        UUID tenantId = UUID.randomUUID();
        seedTenantRow(tenantId);

        TenantContext.runWith(
                tenantId,
                () -> {
                    byte[] plaintext =
                            "calendar-refresh-token-do-not-use-v1".getBytes(StandardCharsets.UTF_8);
                    byte[] envelope =
                            oAuthTokenStore.encrypt(
                                    plaintext, tenantId, RowDiscriminator.CALENDAR_CONNECTION);

                    CalendarConnectionEntity persisted =
                            new CalendarConnectionEntity(
                                    UUID.randomUUID(),
                                    tenantId,
                                    "user-" + UUID.randomUUID() + "@example.test",
                                    CalendarConnectionStatus.CONNECTED);
                    persisted.setRefreshTokenEncrypted(envelope);
                    persisted.setConnectedAt(Instant.now());
                    persisted.setScopesGranted("scope-placeholder");
                    calendarConnectionRepository.saveAndFlush(persisted);

                    Optional<CalendarConnectionEntity> reloaded =
                            calendarConnectionRepository.findByIdAndTenantId(
                                    persisted.getId(), tenantId);
                    assertThat(reloaded)
                            .as("calendar_connections row must be reloadable")
                            .isPresent();

                    byte[] decrypted =
                            oAuthTokenStore.decrypt(
                                    reloaded.orElseThrow().getRefreshTokenEncrypted(),
                                    tenantId,
                                    RowDiscriminator.CALENDAR_CONNECTION);
                    assertThat(decrypted)
                            .as("OAuthTokenStore round-trip must yield the original plaintext")
                            .isEqualTo(plaintext);
                    assertThat(reloaded.orElseThrow().getStatus())
                            .as("status must persist as CONNECTED")
                            .isEqualTo(CalendarConnectionStatus.CONNECTED);
                });
    }

    @Test
    void calendar_oauth_path_never_writes_to_gmail_connections_refresh_token() {
        UUID tenantId = UUID.randomUUID();
        seedTenantRow(tenantId);

        TenantContext.runWith(
                tenantId,
                () -> {
                    byte[] gmailPlaintext =
                            "gmail-refresh-token-do-not-use-v1".getBytes(StandardCharsets.UTF_8);
                    byte[] gmailEnvelope =
                            oAuthTokenStore.encrypt(
                                    gmailPlaintext, tenantId, RowDiscriminator.GMAIL_CONNECTION);
                    GmailConnectionEntity gmailConnection =
                            new GmailConnectionEntity(
                                    UUID.randomUUID(),
                                    tenantId,
                                    "gmail-" + UUID.randomUUID() + "@example.test",
                                    GmailConnectionStatus.CONNECTED);
                    gmailConnection.setRefreshTokenEncrypted(gmailEnvelope);
                    gmailConnection.setConnectedAt(Instant.now());
                    gmailConnection.setScopesGranted("gmail.modify-placeholder");
                    gmailConnectionRepository.saveAndFlush(gmailConnection);
                    byte[] gmailBytesBeforeCalendarWrite =
                            gmailConnectionRepository
                                    .findByIdAndTenantId(gmailConnection.getId(), tenantId)
                                    .orElseThrow()
                                    .getRefreshTokenEncrypted()
                                    .clone();

                    byte[] calendarPlaintext =
                            "calendar-refresh-token-do-not-use-v1".getBytes(StandardCharsets.UTF_8);
                    byte[] calendarEnvelope =
                            oAuthTokenStore.encrypt(
                                    calendarPlaintext,
                                    tenantId,
                                    RowDiscriminator.CALENDAR_CONNECTION);
                    CalendarConnectionEntity calendarConnection =
                            new CalendarConnectionEntity(
                                    UUID.randomUUID(),
                                    tenantId,
                                    "calendar-" + UUID.randomUUID() + "@example.test",
                                    CalendarConnectionStatus.CONNECTED);
                    calendarConnection.setRefreshTokenEncrypted(calendarEnvelope);
                    calendarConnection.setConnectedAt(Instant.now());
                    calendarConnection.setScopesGranted("calendar-scope-placeholder");
                    calendarConnectionRepository.saveAndFlush(calendarConnection);

                    byte[] gmailBytesAfterCalendarWrite =
                            gmailConnectionRepository
                                    .findByIdAndTenantId(gmailConnection.getId(), tenantId)
                                    .orElseThrow()
                                    .getRefreshTokenEncrypted();
                    assertThat(gmailBytesAfterCalendarWrite)
                            .as(
                                    "T-12-05 Pitfall 2: gmail_connections.refresh_token_encrypted"
                                            + " must be byte-identical pre/post Calendar OAuth"
                                            + " write")
                            .isEqualTo(gmailBytesBeforeCalendarWrite);

                    byte[] calendarBytesAfter =
                            calendarConnectionRepository
                                    .findByIdAndTenantId(calendarConnection.getId(), tenantId)
                                    .orElseThrow()
                                    .getRefreshTokenEncrypted();
                    byte[] decryptedCalendar =
                            oAuthTokenStore.decrypt(
                                    calendarBytesAfter,
                                    tenantId,
                                    RowDiscriminator.CALENDAR_CONNECTION);
                    assertThat(decryptedCalendar)
                            .as(
                                    "the Calendar row's ciphertext must decrypt to the Calendar"
                                            + " plaintext")
                            .isEqualTo(calendarPlaintext);
                });
    }

    @Test
    void calendar_connection_entity_has_no_gmail_connection_id_field() {
        boolean hasGmailFk =
                java.util.Arrays.stream(CalendarConnectionEntity.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName)
                        .anyMatch(name -> name.equalsIgnoreCase("gmailConnectionId"));
        assertThat(hasGmailFk)
                .as(
                        "CAL-CONN-06 invariant: CalendarConnectionEntity must NOT carry a"
                                + " gmail_connection_id FK at the entity layer either")
                .isFalse();
    }

    /**
     * Seeds a minimal {@code tenants} row (referenced by {@code tenant_id} FK on the Calendar +
     * Gmail rows). Using {@code JdbcTemplate} keeps the test independent of the {@code core.tenant}
     * service layer.
     */
    private void seedTenantRow(UUID tenantId) {
        Integer existing =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM tenants WHERE id = ?", Integer.class, tenantId);
        if (existing != null && existing > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO tenants (id, display_name, created_at) VALUES (?, ?, now())",
                tenantId,
                "test-tenant-" + tenantId);
    }
}
