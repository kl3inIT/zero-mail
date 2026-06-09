package com.zeromail.core.inbox.persistence;

import java.io.Serializable;
import java.util.UUID;

/** Composite PK for gmail_inbox_sync_state: (tenant_id, gmail_connection_id). */
public record GmailInboxSyncStateId(UUID tenantId, UUID gmailConnectionId)
        implements Serializable {}
