package com.zeromail.core.admin.auth.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum AdminStatus implements IdentifiedEnum {
    PENDING_ENROLLMENT,
    ACTIVE,
    REVOKED;

    @Override
    public String id() {
        return name();
    }

    public static AdminStatus fromId(String id) {
        return Stream.of(values())
                .filter(adminStatus -> adminStatus.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown AdminStatus id: " + id));
    }
}
