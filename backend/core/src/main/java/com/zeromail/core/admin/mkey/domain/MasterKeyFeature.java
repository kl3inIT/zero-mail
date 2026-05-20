package com.zeromail.core.admin.mkey.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum MasterKeyFeature implements IdentifiedEnum {
    CHAT,
    TRIAGE,
    DRAFT;

    @JsonValue
    @Override
    public String id() {
        return name();
    }

    public String columnName() {
        return switch (this) {
            case CHAT -> "feature_default_provider_chat";
            case TRIAGE -> "feature_default_provider_triage";
            case DRAFT -> "feature_default_provider_draft";
        };
    }

    @JsonCreator
    public static MasterKeyFeature fromId(String id) {
        return Stream.of(values())
                .filter(masterKeyFeature -> masterKeyFeature.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown MasterKeyFeature id: " + id));
    }
}
