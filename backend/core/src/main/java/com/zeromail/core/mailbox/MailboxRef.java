package com.zeromail.core.mailbox;

import java.util.UUID;

/**
 * Mailbox-scoped Gmail client identity.
 *
 * <p>The tenant id remains the AES-GCM additional authenticated data for legacy refresh-token
 * envelopes, while the Gmail connection id is the runtime cache key. Carrying both values in one
 * type makes a tenant-only mailbox client lookup unrepresentable in mailbox-scoped flows.
 *
 * <p>Owned by the {@code mailbox} module: it is the value identity of a mailbox. The {@code gmail}
 * module depends on this type to key its per-mailbox client factory; the dependency points {@code
 * gmail -> mailbox} only, so the two modules form no cycle.
 */
public record MailboxRef(UUID tenantId, UUID gmailConnectionId) {}
