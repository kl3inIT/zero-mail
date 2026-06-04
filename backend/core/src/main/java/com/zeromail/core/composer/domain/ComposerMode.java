package com.zeromail.core.composer.domain;

import java.util.NoSuchElementException;

/**
 * Which inbox-composer action the user invoked. The mode shapes the MIME headers we set when we
 * write the draft to Gmail — replies and reply-alls carry {@code In-Reply-To} + {@code References},
 * forwards do not.
 */
public enum ComposerMode {
    REPLY("reply"),
    REPLY_ALL("reply_all"),
    FORWARD("forward");

    private final String id;

    ComposerMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean isReply() {
        return this == REPLY || this == REPLY_ALL;
    }

    public static ComposerMode fromId(String id) {
        if (id == null) {
            throw new NoSuchElementException("ComposerMode id must not be null");
        }
        for (ComposerMode mode : values()) {
            if (mode.id.equalsIgnoreCase(id)) {
                return mode;
            }
        }
        throw new NoSuchElementException("Unknown ComposerMode id: " + id);
    }
}
