package com.zeromail.api.security;

import java.util.UUID;

public record OAuthIntentSnapshot(String intent, UUID targetMailboxId, UUID initiatingTenantId) {

    public static final String INTENT_FIRST_LOGIN = "first_login";
    public static final String INTENT_ADD_MAILBOX = "add_mailbox";
    public static final String INTENT_RECONNECT_MAILBOX = "reconnect_mailbox";

    public static final String ATTRIBUTE_INTENT = "intent";
    public static final String ATTRIBUTE_TARGET_MAILBOX_ID = "targetMailboxId";
    public static final String ATTRIBUTE_INITIATING_TENANT_ID = "initiatingTenantId";

    public static final String PENDING_INTENT_SESSION_ATTRIBUTE = "ZEROMAIL_OAUTH_PENDING_INTENT";
    public static final String CALLBACK_INTENT_SESSION_ATTRIBUTE = "ZEROMAIL_OAUTH_INTENT";
    public static final String INITIATING_SECURITY_CONTEXT_SESSION_ATTRIBUTE =
            "ZEROMAIL_OAUTH_INITIATING_SECURITY_CONTEXT";

    public static final String PENDING_SESSION_ATTRIBUTE = PENDING_INTENT_SESSION_ATTRIBUTE;
}
