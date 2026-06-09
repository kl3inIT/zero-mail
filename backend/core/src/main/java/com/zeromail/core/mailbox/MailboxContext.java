package com.zeromail.core.mailbox;

import com.zeromail.core.gmail.gateway.MailboxRef;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MailboxContext {

    public static final ScopedValue<UUID> MAILBOX = ScopedValue.newInstance();

    private MailboxContext() {}

    public static UUID currentOrThrow() {
        if (!MAILBOX.isBound()) {
            throw new IllegalStateException("No MailboxContext mailbox bound on this thread");
        }
        return MAILBOX.get();
    }

    public static Optional<UUID> currentOptional() {
        return MAILBOX.isBound() ? Optional.of(MAILBOX.get()) : Optional.empty();
    }

    /** Canonical mailbox rebind helper for worker and automation consumers. */
    public static void runWith(MailboxRef mailboxRef, Runnable action) {
        Objects.requireNonNull(mailboxRef, "mailboxRef must not be null");
        ScopedValue.where(MAILBOX, mailboxRef.gmailConnectionId()).run(action);
    }
}
