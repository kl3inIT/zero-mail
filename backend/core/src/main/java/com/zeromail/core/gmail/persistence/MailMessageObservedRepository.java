package com.zeromail.core.gmail.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MailMessageObservedRepository
        extends JpaRepository<MailMessageObservedEntity, MailMessageObservedId> {

    @Modifying
    @Query(
            value =
                    """
            INSERT INTO mail_message_observed
              (tenant_id, gmail_message_id, gmail_thread_id, history_id, label_ids, internal_date,
               sender_email, list_unsubscribe_url, list_unsubscribe_mailto,
               list_unsubscribe_one_click, observed_at)
            VALUES
              (:tenantId, :gmailMessageId, :gmailThreadId, :historyId, :labelIds, :internalDate,
               :senderEmail, :listUnsubscribeUrl, :listUnsubscribeMailto,
               :listUnsubscribeOneClick, NOW())
            ON CONFLICT (tenant_id, gmail_message_id) DO NOTHING
            """,
            nativeQuery = true)
    @Transactional
    int insertObservedIfAbsent(
            @Param("tenantId") UUID tenantId,
            @Param("gmailMessageId") String gmailMessageId,
            @Param("gmailThreadId") String gmailThreadId,
            @Param("historyId") Long historyId,
            @Param("labelIds") String[] labelIds,
            @Param("internalDate") Long internalDate,
            @Param("senderEmail") String senderEmail,
            @Param("listUnsubscribeUrl") String listUnsubscribeUrl,
            @Param("listUnsubscribeMailto") String listUnsubscribeMailto,
            @Param("listUnsubscribeOneClick") boolean listUnsubscribeOneClick);
}
