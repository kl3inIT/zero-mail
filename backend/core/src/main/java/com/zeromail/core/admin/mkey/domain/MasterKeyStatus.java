package com.zeromail.core.admin.mkey.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;

/**
 * Lifecycle of a single platform-side LLM provider master key row.
 *
 * <ul>
 *   <li>{@link #PENDING} — newly created, not yet probed; never selected by the router.
 *   <li>{@link #ACTIVE} — verified at least once; eligible for the priority walk.
 *   <li>{@link #REVOKED} — operator-disabled; kept for audit but never selected.
 * </ul>
 */
public enum MasterKeyStatus implements IdentifiedEnum {
    PENDING("PENDING"),
    ACTIVE("ACTIVE"),
    REVOKED("REVOKED");

    private final String id;

    MasterKeyStatus(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public static MasterKeyStatus fromId(String id) {
        for (MasterKeyStatus status : values()) {
            if (status.id.equals(id)) return status;
        }
        throw new NoSuchElementException("Unknown MasterKeyStatus id: " + id);
    }
}
