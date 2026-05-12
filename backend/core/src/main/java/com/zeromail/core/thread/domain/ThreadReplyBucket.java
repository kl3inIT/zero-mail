package com.zeromail.core.thread.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum ThreadReplyBucket implements IdentifiedEnum {
    TO_REPLY("to-reply"),
    AWAITING_THEIR_REPLY("awaiting-their-reply"),
    FYI("fyi"),
    ACTIONED("actioned");

    private final String publicSlug;

    ThreadReplyBucket(String publicSlug) {
        this.publicSlug = publicSlug;
    }

    @Override
    public String id() {
        return name();
    }

    public String publicSlug() {
        return publicSlug;
    }

    public static ThreadReplyBucket fromId(String id) {
        return Stream.of(values())
                .filter(threadReplyBucket -> threadReplyBucket.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown ThreadReplyBucket id: " + id));
    }

    public static ThreadReplyBucket fromPublicSlug(String publicSlug) {
        return Stream.of(values())
                .filter(threadReplyBucket -> threadReplyBucket.publicSlug().equals(publicSlug))
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Unknown ThreadReplyBucket slug: " + publicSlug));
    }
}
