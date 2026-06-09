package com.zeromail.core.gmail.gateway;

import java.util.UUID;

/**
 * Mailbox-scoped Gmail client identity.
 *
 * <p>The tenant id remains the AES-GCM additional authenticated data for legacy refresh-token
 * envelopes, while the Gmail connection id is the runtime cache key. Carrying both values in one
 * type makes a tenant-only mailbox client lookup unrepresentable in mailbox-scoped flows.
 */
public record MailboxRef(UUID tenantId, UUID gmailConnectionId) {}
