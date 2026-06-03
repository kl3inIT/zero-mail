package com.zeromail.core.rules.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum RuleTemplateStatus implements IdentifiedEnum {
    MATERIALIZABLE("materializable"),
    GALLERY_ONLY("gallery_only"),
    // Auto-seeded (enabled) on first login as the starter rule set. Resolvable into a tenant rule
    // like MATERIALIZABLE, but intentionally hidden from the browsable template gallery so the
    // first-login defaults do not also clutter the "add a template" catalog.
    SYSTEM_DEFAULT("system_default"),
    DEPRECATED("deprecated");

    private final String id;

    RuleTemplateStatus(String id) {
        this.id = id;
    }

    @JsonValue
    @Override
    public String id() {
        return id;
    }

    @JsonCreator
    public static RuleTemplateStatus fromId(String id) {
        return Stream.of(values())
                .filter(ruleTemplateStatus -> ruleTemplateStatus.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown RuleTemplateStatus id: " + id));
    }
}
