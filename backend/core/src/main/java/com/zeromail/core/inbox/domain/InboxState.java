package com.zeromail.core.inbox.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Lifecycle of a Gmail message in the inbox projection.
 *
 * <ul>
 *   <li>{@link #INBOX} — message currently carries the Gmail {@code INBOX} label; visible in the
 *       inbox list query (partial index filter).
 *   <li>{@link #OUT_OF_INBOX} — was observed in INBOX previously, no longer carries the label
 *       (archived, moved, trashed). Row kept so reconciliation and idempotent re-observation can
 *       distinguish "never seen" from "seen and dropped."
 *   <li>{@link #TOMBSTONED} — Gmail returned 404 / messageDeleted; row kept short-term then purged
 *       by the deferred TTL job.
 * </ul>
 *
 * <p>Phase A intentionally omits SENT / DRAFT / TRASH / SPAM — this projection is the inbox list
 * read model, not a mailbox-wide message store. A mailbox/thread projection is a separate phase.
 */
public enum InboxState implements IdentifiedEnum {
    INBOX,
    OUT_OF_INBOX,
    TOMBSTONED;

    @Override
    public String id() {
        return name();
    }

    public static InboxState fromId(String id) {
        return Stream.of(values())
                .filter(state -> state.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown InboxState id: " + id));
    }
}
