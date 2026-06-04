package com.zeromail.core.inbox.domain;

/**
 * Field names used in the AAD of the inbox projection cipher. Stable enum on purpose so a typo in a
 * string literal cannot silently break decryption invariants.
 */
public enum EncryptedField {
    SENDER_EMAIL("sender_email"),
    SENDER_DISPLAY_NAME("sender_display_name"),
    SUBJECT("subject"),
    SNIPPET("snippet");

    private final String aadName;

    EncryptedField(String aadName) {
        this.aadName = aadName;
    }

    public String aadName() {
        return aadName;
    }
}
