package com.zeromail.core.inbox.persistence;

import com.zeromail.core.inbox.domain.InboxState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

/**
 * Per-message Gmail inbox display projection (Phase A).
 *
 * <p>Ciphertext columns are populated via {@code InboxProjectionCipher} at the use-case boundary;
 * the entity itself only carries raw bytes. The denormalized {@code unread} flag is derived from
 * {@link #labelIds} by the write service for index efficiency on the inbox list query.
 */
@Entity
@Table(name = "gmail_inbox_projection")
@IdClass(GmailInboxProjectionId.class)
@SuppressWarnings({"JpaDataSourceORMInspection", "unused"})
public class GmailInboxProjectionEntity {

    @Id
    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "gmail_message_id", nullable = false)
    private String gmailMessageId;

    @Column(name = "gmail_thread_id", nullable = false)
    private String gmailThreadId;

    @Column(name = "sender_email_hash", nullable = false)
    private byte[] senderEmailHash;

    @Column(name = "sender_email_ciphertext", nullable = false)
    private byte[] senderEmailCiphertext;

    @Column(name = "sender_display_name_ciphertext")
    private byte[]senderDisplayNameCiphertext;

    @Column(name = "subject_ciphertext")
    private byte[]subjectCiphertext;

    @Column(name = "snippet_ciphertext")
    private byte[]snippetCiphertext;

    @Column(name = "has_attachment", nullable = false)
    private boolean hasAttachment;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "label_ids", columnDefinition = "text[]", nullable = false)
    private String[] labelIds;

    @Column(name = "inbox_state", nullable = false, length = 16)
    private String inboxState = InboxState.INBOX.id();

    @Column(name = "unread", nullable = false)
    private boolean unread;

    @Column(name = "source_history_id", nullable = false)
    private long sourceHistoryId;

    @Column(name = "refreshed_at", nullable = false)
    private Instant refreshedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected GmailInboxProjectionEntity() {
        // Hibernate
    }

    public GmailInboxProjectionEntity(
            UUID tenantId,
            String gmailMessageId,
            String gmailThreadId,
            byte[] senderEmailHash,
            byte[] senderEmailCiphertext,
            byte[]senderDisplayNameCiphertext,
            byte[]subjectCiphertext,
            byte[]snippetCiphertext,
            boolean hasAttachment,
            Instant receivedAt,
            String[] labelIds,
            InboxState inboxState,
            boolean unread,
            long sourceHistoryId,
            Instant refreshedAt,
            Instant expiresAt) {
        this.tenantId = tenantId;
        this.gmailMessageId = gmailMessageId;
        this.gmailThreadId = gmailThreadId;
        this.senderEmailHash = senderEmailHash;
        this.senderEmailCiphertext = senderEmailCiphertext;
        this.senderDisplayNameCiphertext = senderDisplayNameCiphertext;
        this.subjectCiphertext = subjectCiphertext;
        this.snippetCiphertext = snippetCiphertext;
        this.hasAttachment = hasAttachment;
        this.receivedAt = receivedAt;
        this.labelIds = labelIds;
        this.inboxState = inboxState.id();
        this.unread = unread;
        this.sourceHistoryId = sourceHistoryId;
        this.refreshedAt = refreshedAt;
        this.expiresAt = expiresAt;
    }

    public GmailInboxProjectionId getId() {
        return new GmailInboxProjectionId(tenantId, gmailMessageId);
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public String getGmailThreadId() {
        return gmailThreadId;
    }

    public byte[] getSenderEmailHash() {
        return senderEmailHash;
    }

    public byte[] getSenderEmailCiphertext() {
        return senderEmailCiphertext;
    }

    public byte[]getSenderDisplayNameCiphertext() {
        return senderDisplayNameCiphertext;
    }

    public byte[]getSubjectCiphertext() {
        return subjectCiphertext;
    }

    public byte[]getSnippetCiphertext() {
        return snippetCiphertext;
    }

    public boolean isHasAttachment() {
        return hasAttachment;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String[] getLabelIds() {
        return labelIds;
    }

    public String getInboxStateId() {
        return inboxState;
    }

    public InboxState getInboxState() {
        return InboxState.fromId(inboxState);
    }

    public boolean isUnread() {
        return unread;
    }

    public long getSourceHistoryId() {
        return sourceHistoryId;
    }

    public Instant getRefreshedAt() {
        return refreshedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getVersion() {
        return version;
    }
}
