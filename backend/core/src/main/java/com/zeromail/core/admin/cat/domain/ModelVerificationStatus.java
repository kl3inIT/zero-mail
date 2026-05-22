package com.zeromail.core.admin.cat.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;

/**
 * Verification lifecycle for a row in model_catalog.
 *
 * <ul>
 *   <li>{@link #UNTESTED} — newly-discovered model, never probed.
 *   <li>{@link #VERIFIED} — last probe succeeded; routable through the 3-tier picker.
 *   <li>{@link #STALE} — last probe was VERIFIED but is older than the freshness window; routable
 *       but operators should re-test soon.
 *   <li>{@link #FAILED} — last probe returned an error; never selected.
 * </ul>
 */
public enum ModelVerificationStatus implements IdentifiedEnum {
    UNTESTED("UNTESTED"),
    VERIFIED("VERIFIED"),
    STALE("STALE"),
    FAILED("FAILED");

    private final String id;

    ModelVerificationStatus(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public static ModelVerificationStatus fromId(String id) {
        for (ModelVerificationStatus status : values()) {
            if (status.id.equals(id)) return status;
        }
        throw new NoSuchElementException("Unknown ModelVerificationStatus id: " + id);
    }
}
