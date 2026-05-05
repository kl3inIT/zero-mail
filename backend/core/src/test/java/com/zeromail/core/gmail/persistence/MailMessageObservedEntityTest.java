package com.zeromail.core.gmail.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;

class MailMessageObservedEntityTest extends PostgresContainerTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired MailMessageObservedRepository observed;

  @Test
  void insertAndRead_compositePk_roundtrip() {
    UUID tenantId = seedTenant();
    MailMessageObservedEntity entity =
        new MailMessageObservedEntity(
            tenantId,
            "gmail-1",
            "thread-1",
            123L,
            new String[] {"INBOX", "UNREAD"},
            1_700_000_000_000L,
            Instant.now());

    ScopedValue.where(TenantContext.TENANT, tenantId.toString())
        .run(() -> observed.saveAndFlush(entity));

    MailMessageObservedEntity found =
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
            .call(
                () ->
                    observed
                        .findById(new MailMessageObservedId(tenantId, "gmail-1"))
                        .orElseThrow());
    assertThat(found.getTenantId()).isEqualTo(tenantId);
    assertThat(found.getGmailMessageId()).isEqualTo("gmail-1");
    assertThat(found.getGmailThreadId()).isEqualTo("thread-1");
    assertThat(found.getHistoryId()).isEqualTo(123L);
    assertThat(found.getInternalDate()).isEqualTo(1_700_000_000_000L);
  }

  @Test
  void labelIds_textArray_roundtrip() {
    UUID tenantId = seedTenant();
    insertObserved(tenantId, "array-roundtrip", "thread-array", "ARRAY['INBOX','UNREAD']");

    String[] labels =
        jdbc.queryForObject(
            "SELECT label_ids FROM mail_message_observed WHERE tenant_id = ? AND gmail_message_id = ?",
            (rs, rowNum) -> {
              assertThat(rowNum).isZero();
              return (String[]) rs.getArray(1).getArray();
            },
            tenantId,
            "array-roundtrip");
    assertThat(labels).containsExactly("INBOX", "UNREAD");
  }

  @Test
  void onConflictDoNothing_deduplication() {
    UUID tenantId = seedTenant();

    int first =
        observed.insertObservedIfAbsent(
            tenantId,
            "gmail-dupe",
            "thread-dupe",
            100L,
            new String[] {"INBOX"},
            1_700_000_000_000L);
    int second =
        observed.insertObservedIfAbsent(
            tenantId,
            "gmail-dupe",
            "thread-dupe",
            101L,
            new String[] {"INBOX"},
            1_700_000_000_001L);

    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM mail_message_observed WHERE tenant_id = ? AND gmail_message_id = ?",
            Long.class,
            tenantId,
            "gmail-dupe");
    assertThat(first).isEqualTo(1);
    assertThat(second).isZero();
    assertThat(count).isEqualTo(1L);
  }

  @Test
  void tenantIdFilter_blocksCrossTenantJpaReads() {
    UUID tenantA = seedTenant();
    UUID tenantB = seedTenant();
    insertObserved(tenantA, "message-a", "thread-a", "ARRAY['INBOX']");
    insertObserved(tenantB, "message-b", "thread-b", "ARRAY['INBOX']");

    List<MailMessageObservedEntity> visible =
        ScopedValue.where(TenantContext.TENANT, tenantA.toString()).call(observed::findAll);

    assertThat(visible).extracting(MailMessageObservedEntity::getTenantId).containsOnly(tenantA);
  }

  private UUID seedTenant() {
    UUID tenantId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, "tenant-" + tenantId);
    return tenantId;
  }

  private void insertObserved(UUID tenantId, String messageId, String threadId, String labelsSql) {
    jdbc.update(
        """
                INSERT INTO mail_message_observed(
                    tenant_id, gmail_message_id, gmail_thread_id, history_id, label_ids, internal_date
                )
                VALUES (?, ?, ?, ?, %s::text[], ?)
                ON CONFLICT (tenant_id, gmail_message_id) DO NOTHING
                """
            .formatted(labelsSql),
        tenantId,
        messageId,
        threadId,
        100L,
        1_700_000_000_000L);
  }
}
