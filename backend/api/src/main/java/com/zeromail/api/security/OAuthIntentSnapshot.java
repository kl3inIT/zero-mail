package com.zeromail.api.security;

import java.util.UUID;

public record OAuthIntentSnapshot(String intent, UUID targetMailboxId, UUID initiatingTenantId) {

    public static final String INTENT_FIRST_LOGIN = "first_login";
    public static final String INTENT_ADD_MAILBOX = "add_mailbox";
    public static final String INTENT_RECONNECT_MAILBOX = "reconnect_mailbox";

    /**
     * Phase 12 W3 (CAL-CONN-01 / D-06). Stamped by {@code CalendarConnectIntentController} when the
     * user clicks "Connect Google Calendar" from the /settings/mailboxes/{mailboxId}/calendar page;
     * carries the active {@code mailboxId} into the OAuth round-trip so {@code
     * CalendarOAuthSuccessHandler} can seed default role rows for that mailbox only.
     */
    public static final String INTENT_CALENDAR_CONNECT = "calendar_connect";

    public static final String ATTRIBUTE_INTENT = "intent";
    public static final String ATTRIBUTE_TARGET_MAILBOX_ID = "targetMailboxId";
    public static final String ATTRIBUTE_INITIATING_TENANT_ID = "initiatingTenantId";

    public static final String PENDING_INTENT_SESSION_ATTRIBUTE = "ZEROMAIL_OAUTH_PENDING_INTENT";
    public static final String CALLBACK_INTENT_SESSION_ATTRIBUTE = "ZEROMAIL_OAUTH_INTENT";
    public static final String INITIATING_SECURITY_CONTEXT_SESSION_ATTRIBUTE =
            "ZEROMAIL_OAUTH_INITIATING_SECURITY_CONTEXT";

    public static final String PENDING_SESSION_ATTRIBUTE = PENDING_INTENT_SESSION_ATTRIBUTE;
}
