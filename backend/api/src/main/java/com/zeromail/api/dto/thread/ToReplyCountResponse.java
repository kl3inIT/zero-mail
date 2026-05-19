package com.zeromail.api.dto.thread;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = "toReplyCount")
public record ToReplyCountResponse(long toReplyCount) {

    public static ToReplyCountResponse from(long toReplyCount) {
        return new ToReplyCountResponse(toReplyCount);
    }
}
