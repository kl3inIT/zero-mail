package com.zeromail.core.gmail.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MailMessageObservedRepository
        extends JpaRepository<MailMessageObservedEntity, MailMessageObservedId> {

    default int insertIfAbsent(UUID tenantId, String gmailMessageId, String gmailThreadId,
                               Long historyId, String[] labelIds, Long internalDate) {
        return insertObservedIfAbsent(tenantId, gmailMessageId, gmailThreadId, historyId, labelIds, internalDate);
    }

    @Modifying
    @Query(value = """
            INSERT INTO mail_message_observed
              (tenant_id, gmail_message_id, gmail_thread_id, history_id, label_ids, internal_date, observed_at)
            VALUES
              (:tenantId, :gmailMessageId, :gmailThreadId, :historyId, :labelIds, :internalDate, NOW())
            ON CONFLICT (tenant_id, gmail_message_id) DO NOTHING
            """, nativeQuery = true)
    @Transactional
    int insertObservedIfAbsent(@Param("tenantId") UUID tenantId,
                               @Param("gmailMessageId") String gmailMessageId,
                               @Param("gmailThreadId") String gmailThreadId,
                               @Param("historyId") Long historyId,
                               @Param("labelIds") String[] labelIds,
                               @Param("internalDate") Long internalDate);
}
