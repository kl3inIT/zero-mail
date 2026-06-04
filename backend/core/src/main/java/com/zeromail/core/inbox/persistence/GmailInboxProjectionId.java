package com.zeromail.core.inbox.persistence;

import java.io.Serializable;
import java.util.UUID;

/** Composite PK for {@link GmailInboxProjectionEntity}: (tenant_id, gmail_message_id). */
public record GmailInboxProjectionId(UUID tenantId, String gmailMessageId)
        implements Serializable {}
