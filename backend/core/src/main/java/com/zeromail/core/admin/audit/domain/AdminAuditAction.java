package com.zeromail.core.admin.audit.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum AdminAuditAction implements IdentifiedEnum {
    ADMIN_BOOTSTRAP_CREATED,
    ADMIN_GRANTED,
    ADMIN_REVOKED,
    ADMIN_ROLE_GRANTED,
    ADMIN_ROLE_REVOKED,
    ADMIN_PASSKEY_REGISTERED,
    ADMIN_LOGIN,
    ADMIN_READ,
    MASTER_KEY_TESTED,
    MASTER_KEY_SET,
    MASTER_KEY_ROTATED,
    MASTER_KEY_ROTATION_FAILED,
    MASTER_KEY_FEATURE_DEFAULT_SET,
    CATALOG_SYNC_FETCH_STARTED,
    CATALOG_SYNC_CONFIRMED,
    CATALOG_SYNC_CANCELLED,
    CATALOG_MODEL_CREATED,
    CATALOG_MODEL_DISABLED,
    CATALOG_FEATURE_DEFAULT_SET,
    ADMIN_RESPONSE_BODY_BAN_TRIPPED,
    TENANT_PAUSED,
    TENANT_DISCONNECTED,
    TENANT_DELETED,
    WEBAUTHN_REPLAY_SUSPECTED;

    @Override
    public String id() {
        return name();
    }

    public static AdminAuditAction fromId(String id) {
        return Stream.of(values())
                .filter(adminAuditAction -> adminAuditAction.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown AdminAuditAction id: " + id));
    }
}
