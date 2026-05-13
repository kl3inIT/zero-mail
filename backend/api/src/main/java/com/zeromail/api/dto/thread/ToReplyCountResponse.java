package com.zeromail.api.dto.thread;

public record ToReplyCountResponse(long toReplyCount) {

    public static ToReplyCountResponse from(long toReplyCount) {
        return new ToReplyCountResponse(toReplyCount);
    }
}
