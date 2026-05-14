package com.zeromail.core.notification.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum ChannelType implements IdentifiedEnum {
    EMAIL;

    @Override
    public String id() {
        return name();
    }

    public static ChannelType fromId(String id) {
        return Stream.of(values())
                .filter(channelType -> channelType.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown ChannelType id: " + id));
    }
}
