package com.zeromail.core.inbox.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.inbox.domain.InboxState;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Slice test for the projection UPSERT. We exercise the native query (Hibernate {@code save*} is
 * unused by production code) and check two invariants: first call inserts a row with version 0;
 * second call for the same (tenant, gmail_message_id) updates in place and bumps version.
 */
class GmailInboxProjectionRepositoryTest extends PostgresContainerTest {

    @Autowired GmailInboxProjectionRepository projectionRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void upsert_inserts_new_row_with_version_zero() {
        UUID tenantId = seedTenant();
        String gmailMessageId = "190000000000aa01";
        Instant receivedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant refreshedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt = refreshedAt.plus(Duration.ofDays(90));

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                projectionRepository.upsertProjection(
                                        tenantId,
                                        gmailMessageId,
                                        "thread-abc",
                                        new byte[32],
                                        new byte[64],
                                        null,
                                        null,
                                        null,
                                        false,
                                        receivedAt,
                                        new String[] {"INBOX", "UNREAD"},
                                        InboxState.INBOX.id(),
                                        true,
                                        12345L,
                                        refreshedAt,
                                        expiresAt));

        Optional<GmailInboxProjectionEntity> loaded =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        projectionRepository.findById(
                                                new GmailInboxProjectionId(
                                                        tenantId, gmailMessageId)));

        assertThat(loaded).isPresent();
        GmailInboxProjectionEntity projection = loaded.orElseThrow();
        assertThat(projection.getVersion()).isZero();
        assertThat(projection.getSourceHistoryId()).isEqualTo(12345L);
        assertThat(projection.getInboxState()).isEqualTo(InboxState.INBOX);
        assertThat(projection.isUnread()).isTrue();
        assertThat(projection.getLabelIds()).containsExactly("INBOX", "UNREAD");
    }

    @Test
    void upsert_second_call_for_same_key_updates_in_place_and_bumps_version() {
        UUID tenantId = seedTenant();
        String gmailMessageId = "190000000000aa02";
        Instant initialRefreshedAt = Instant.now().minus(Duration.ofMinutes(5));
        Instant secondRefreshedAt = Instant.now();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            projectionRepository.upsertProjection(
                                    tenantId,
                                    gmailMessageId,
                                    "thread-original",
                                    new byte[32],
                                    new byte[64],
                                    null,
                                    null,
                                    null,
                                    false,
                                    Instant.now().minus(Duration.ofDays(1)),
                                    new String[] {"INBOX"},
                                    InboxState.INBOX.id(),
                                    true,
                                    100L,
                                    initialRefreshedAt,
                                    initialRefreshedAt.plus(Duration.ofDays(90)));
                            projectionRepository.upsertProjection(
                                    tenantId,
                                    gmailMessageId,
                                    "thread-still-the-same",
                                    new byte[32],
                                    new byte[64],
                                    null,
                                    null,
                                    null,
                                    true,
                                    Instant.now().minus(Duration.ofDays(1)),
                                    new String[] {"INBOX", "Label_42"},
                                    InboxState.OUT_OF_INBOX.id(),
                                    false,
                                    200L,
                                    secondRefreshedAt,
                                    secondRefreshedAt.plus(Duration.ofDays(90)));
                        });

        GmailInboxProjectionEntity projection =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        projectionRepository
                                                .findById(
                                                        new GmailInboxProjectionId(
                                                                tenantId, gmailMessageId))
                                                .orElseThrow());

        assertThat(projection.getVersion()).isEqualTo(1);
        assertThat(projection.getSourceHistoryId()).isEqualTo(200L);
        assertThat(projection.getInboxState()).isEqualTo(InboxState.OUT_OF_INBOX);
        assertThat(projection.isUnread()).isFalse();
        assertThat(projection.isHasAttachment()).isTrue();
        assertThat(projection.getLabelIds()).containsExactly("INBOX", "Label_42");
    }

    @Test
    void add_label_appends_custom_label_and_bumps_version() {
        UUID tenantId = seedTenant();
        String gmailMessageId = "190000000000aa03";
        seedInboxRow(tenantId, gmailMessageId, new String[] {"INBOX", "UNREAD"});

        int affected =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        projectionRepository.addLabel(
                                                tenantId, gmailMessageId, "Label_42"));

        assertThat(affected).isEqualTo(1);
        GmailInboxProjectionEntity projection = loadRow(tenantId, gmailMessageId);
        assertThat(projection.getLabelIds()).contains("INBOX", "UNREAD", "Label_42");
        assertThat(projection.getInboxState()).isEqualTo(InboxState.INBOX);
        assertThat(projection.getVersion()).isEqualTo(1);
    }

    @Test
    void add_label_is_idempotent_when_label_already_present() {
        UUID tenantId = seedTenant();
        String gmailMessageId = "190000000000aa04";
        seedInboxRow(tenantId, gmailMessageId, new String[] {"INBOX", "Label_42"});

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> projectionRepository.addLabel(tenantId, gmailMessageId, "Label_42"));

        GmailInboxProjectionEntity projection = loadRow(tenantId, gmailMessageId);
        assertThat(projection.getLabelIds()).containsExactly("INBOX", "Label_42");
    }

    @Test
    void remove_inbox_label_flips_state_to_out_of_inbox() {
        UUID tenantId = seedTenant();
        String gmailMessageId = "190000000000aa05";
        seedInboxRow(tenantId, gmailMessageId, new String[] {"INBOX", "Label_42"});

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> projectionRepository.removeLabel(tenantId, gmailMessageId, "INBOX"));

        GmailInboxProjectionEntity projection = loadRow(tenantId, gmailMessageId);
        assertThat(projection.getLabelIds()).containsExactly("Label_42");
        assertThat(projection.getInboxState()).isEqualTo(InboxState.OUT_OF_INBOX);
    }

    @Test
    void label_mirrors_return_zero_when_no_projection_row_exists() {
        UUID tenantId = seedTenant();

        int added =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        projectionRepository.addLabel(
                                                tenantId, "missing-message", "Label_42"));

        assertThat(added).isZero();
    }

    private void seedInboxRow(UUID tenantId, String gmailMessageId, String[] labelIds) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        boolean unread = false;
        for (String labelId : labelIds) {
            if ("UNREAD".equals(labelId)) {
                unread = true;
            }
        }
        boolean unreadFlag = unread;
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                projectionRepository.upsertProjection(
                                        tenantId,
                                        gmailMessageId,
                                        "thread-" + gmailMessageId,
                                        new byte[32],
                                        new byte[64],
                                        null,
                                        null,
                                        null,
                                        false,
                                        now,
                                        labelIds,
                                        InboxState.INBOX.id(),
                                        unreadFlag,
                                        1L,
                                        now,
                                        now.plus(Duration.ofDays(90))));
    }

    private GmailInboxProjectionEntity loadRow(UUID tenantId, String gmailMessageId) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(
                        () ->
                                projectionRepository
                                        .findById(
                                                new GmailInboxProjectionId(
                                                        tenantId, gmailMessageId))
                                        .orElseThrow());
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
